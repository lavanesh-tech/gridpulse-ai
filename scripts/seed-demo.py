#!/usr/bin/env python3
"""Fills a running GridPulse with a synthetic demo depot through the public API (as the admin from .env).

  python3 scripts/seed-demo.py                 # against the Docker stack (http://localhost:3000)
  GRIDPULSE_URL=http://localhost:8080 python3 scripts/seed-demo.py

Creates: one grid connection with a time-of-use tariff, two stations with 8 chargers, a fleet of 12 vans,
the sample policy documents for the advisor, and a few charging sessions booked for tonight.
Safe to run twice: it stops if the demo fleet already exists. All data is synthetic.
"""
import json, os, random, sys, urllib.error, urllib.request
from datetime import datetime, timedelta, timezone

BASE = os.environ.get("GRIDPULSE_URL", "http://localhost:3000").rstrip("/")
FLEET = "Night vans (demo)"


def env_password():
    path = os.path.join(os.path.dirname(__file__), "..", ".env")
    for line in open(path):
        if line.startswith("GRIDPULSE_ADMIN_PASSWORD="):
            return line.split("=", 1)[1].strip()
    sys.exit("GRIDPULSE_ADMIN_PASSWORD not found in .env")


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method,
                                 data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json",
                                          **({"Authorization": "Bearer " + token} if token else {})})
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{method} {path} -> {e.code}: {e.read().decode()[:300]}")
    except urllib.error.URLError as e:
        raise SystemExit(f"Cannot reach {BASE} ({e.reason}). Is the stack running? ./scripts/stack.sh")


def main():
    t = call("POST", "/api/v1/auth/token", {"username": "admin", "password": env_password()})["accessToken"]
    if any(f["name"] == FLEET for f in call("GET", "/api/v1/fleets", token=t)):
        print("Demo data already present - nothing to do.")
        return

    grid = call("POST", "/api/v1/grid-connections", {"name": "Depot main feed", "capacityLimitKw": 450}, t)
    call("PUT", f"/api/v1/grid-connections/{grid['id']}/tariff", {"bands": [
        {"start": "00:00", "end": "06:00", "pricePerKwh": 0.09},
        {"start": "06:00", "end": "17:00", "pricePerKwh": 0.18},
        {"start": "17:00", "end": "21:00", "pricePerKwh": 0.34},
        {"start": "21:00", "end": "00:00", "pricePerKwh": 0.14}]}, t)
    chargers = []
    for station_name, specs in [("North yard", [("N1", "CCS1", 150), ("N2", "CCS1", 150), ("N3", "CCS1", 50), ("N4", "CCS1", 50)]),
                                ("South yard", [("S1", "CCS1", 150), ("S2", "CCS1", 50), ("S3", "NACS", 150), ("S4", "NACS", 50)])]:
        st = call("POST", "/api/v1/stations", {"gridConnectionId": grid["id"], "name": station_name}, t)
        for label, conn, kw in specs:
            chargers.append(call("POST", f"/api/v1/stations/{st['id']}/chargers",
                                 {"label": label, "connectorType": conn, "maxPowerKw": kw}, t))
    print(f"Grid 450 kW with tariff, 2 stations, {len(chargers)} chargers")

    fleet = call("POST", "/api/v1/fleets", {"name": FLEET}, t)
    rnd = random.Random(42)
    alphabet = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789"
    models = [("eTransit", 89, 115, "CCS1"), ("eSprinter", 113, 115, "CCS1"), ("ProMaster EV", 110, 150, "CCS1"),
              ("ID.Buzz Cargo", 82, 170, "NACS")]
    vans = []
    for i in range(12):
        model, kwh, rate, conn = models[i % len(models)]
        vin = "".join(rnd.choice(alphabet) for _ in range(17))
        vans.append(call("POST", f"/api/v1/fleets/{fleet['id']}/vehicles", {
            "vin": vin, "model": model, "batteryCapacityKwh": kwh, "maxChargeRateKw": rate,
            "connectorType": conn, "stateOfChargePct": rnd.randint(12, 55)}, t))
    print(f"Fleet '{FLEET}' with {len(vans)} vans")

    docs = call("POST", "/api/v1/advisor/documents/samples", {}, t)
    print(f"{len(docs)} sample documents for the advisor")

    # Book three sessions tonight (local time) so the dashboard shows something.
    now = datetime.now().astimezone()
    tonight = now.replace(hour=22, minute=0, second=0, microsecond=0)
    if tonight < now:
        tonight += timedelta(days=1)
    ccs_chargers = [c for c in chargers if c["connectorType"] == "CCS1" and c["maxPowerKw"] >= 100]
    ccs_vans = [v for v in vans if v["connectorType"] == "CCS1"]
    for k in range(3):
        start = tonight + timedelta(hours=k)
        call("POST", "/api/v1/sessions", {
            "vehicleId": ccs_vans[k]["id"], "chargerId": ccs_chargers[k]["id"],
            "startsAt": start.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
            "endsAt": (start + timedelta(hours=2)).astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
            "powerKw": 100}, t)
    print("3 sessions booked for tonight")
    print("Done. Open the console and try Plan charging -> Show plan, then Advisor.")


if __name__ == "__main__":
    main()
