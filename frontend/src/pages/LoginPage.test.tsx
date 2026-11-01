import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { LoginPage } from './LoginPage';
import { fakeToken, mockApi, renderWithApp } from '../test/render';
import { getToken, setToken } from '../api/client';

afterEach(() => {
  setToken(null);
  vi.unstubAllGlobals();
});

const app = (
  <Routes>
    <Route path="/sign-in" element={<LoginPage />} />
    <Route path="/" element={<p>Signed-in home</p>} />
  </Routes>
);

describe('LoginPage', () => {
  it('signs in and stores the token', async () => {
    const calls = mockApi([{ method: 'POST', path: '/api/v1/auth/token', body: { accessToken: fakeToken('OPERATOR'), tokenType: 'Bearer', expiresIn: 3600 } }]);
    renderWithApp(app, { route: '/sign-in' });
    await userEvent.type(screen.getByLabelText('Username'), 'maria');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Signed-in home')).toBeInTheDocument();
    expect(calls[0].body).toEqual({ username: 'maria', password: 'correct horse' });
    expect(getToken()).toBe(fakeToken('OPERATOR'));
  });

  it('shows the server message when credentials are wrong', async () => {
    mockApi([{ method: 'POST', path: '/api/v1/auth/token', status: 401, body: { title: 'Unauthorized', detail: 'Invalid credentials' } }]);
    renderWithApp(app, { route: '/sign-in' });
    await userEvent.type(screen.getByLabelText('Username'), 'maria');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid credentials');
    expect(getToken()).toBeNull();
  });
});
