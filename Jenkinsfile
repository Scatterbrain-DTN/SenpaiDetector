pipeline {
    agent any
    environment {
        ANDROID_SDK_ROOT = "$HOME/Android/Sdk"
        ANDROID_HOME = "$ANDROID_SDK_ROOT"
    }

    options {
        skipStagesAfterUnstable()
    }

    stages {
        stage('Build RxAndroidBle') {
            steps {
                dir('RxAndroidBle') {
                    git branch: 'gatt-server', url: 'https://github.com/gnu3ra/RxAndroidBle.git'
                    withGradle {
                        sh './gradlew assembleRelease test uploadArchives'
                    }
                }
            }
        }
        stage('Build SenpaiDetector') {
            steps {
                withGradle {
                    sh './gradlew assembleRelease'
                }
                signAndroidApks (
                    androidHome: "$ANDROID_HOME",
                    apksToSign: 'app/build/outputs/apk/release/app-release-unsigned.apk',
                    keyStoreId: 'android-dev',
                    keyAlias: 'piracy',
                    skipZipalign: true,
                    archiveSignedApks: true
                )
            }
        }
    }
}
