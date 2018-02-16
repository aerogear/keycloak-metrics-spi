#!groovy

// https://github.com/feedhenry/fh-pipeline-library
@Library('fh-pipeline-library') _

stage('Trust') {
    enforceTrustedApproval('aerogear')
}

node('apb-test') {

    stage('Checkout') {
        checkout scm
    }

    stage('Unit tests') {
        sh './gradlew test'
    }

}