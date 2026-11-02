// The same pipeline as .github/workflows/ci.yml, for teams on Jenkins.
// Agent requirements: JDK 21, Node 22, Docker (Testcontainers and image builds), bash.
pipeline {
    agent any
    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    environment {
        CI = 'true'
    }
    stages {
        stage('Backend: tests + coverage gate') {
            steps {
                sh './gradlew build --no-daemon'
            }
            post {
                always {
                    junit testResults: '**/build/test-results/test/*.xml', allowEmptyResults: true
                    archiveArtifacts artifacts: 'build/reports/jacoco/**', allowEmptyArchive: true
                }
            }
        }
        stage('Frontend: unit tests + build') {
            steps {
                dir('frontend') {
                    sh 'npm ci --no-audit --no-fund'
                    sh 'npm test'
                    sh 'npm run build'
                }
            }
        }
        stage('Browser end-to-end') {
            steps {
                sh './scripts/e2e.sh'
            }
            post {
                failure {
                    archiveArtifacts artifacts: 'frontend/playwright-report/**, frontend/test-results/**, build/app-*.log',
                            allowEmptyArchive: true
                }
            }
        }
        stage('Docker images') {
            steps {
                sh 'docker compose --profile app build'
            }
        }
    }
}
