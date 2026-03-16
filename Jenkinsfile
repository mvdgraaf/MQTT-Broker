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