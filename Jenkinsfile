pipeline {
    agent any

    tools {
        maven 'Maven'
        jdk 'JDK25'
    }

    environment {
        IMAGE_NAME = "mqtt"
        IMAGE_TAG  = "${BUILD_NUMBER}"
        GIT_REPO   = "https://github.com/mvdgraaf/MQTT-Broker.git"
        GIT_BRANCH = "master"
    }

    stages {

        stage('Checkout') {
            steps {
                script {
                    git branch: "${GIT_BRANCH}",
                        url: "${GIT_REPO}"
                }
            }
        }

        stage('Build with Maven') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Run Tests') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh """
                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                """
            }
        }
        stage('Login to GHCR') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: '3f73bae6-3c57-47ef-b9ff-01208c582239',
                    usernameVariable: 'GITHUB_USER',
                    passwordVariable: 'GITHUB_TOKEN'
                )]) {
                    sh '''
                        echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USER --password-stdin
                    '''
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
            }
        }
    }

    post {
        success {
            echo 'Build succesvol afgerond 🎉'
        }
        failure {
            echo 'Build mislukt ❌'
        }
    }
}