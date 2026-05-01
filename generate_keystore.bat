@echo off
echo Generating release keystore...

if not exist "keystore" mkdir keystore

keytool -genkeypair ^
  -alias encryptchat ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -keystore keystore\release.jks ^
  -storepass encryptchat2024 ^
  -keypass encryptchat2024 ^
  -dname "CN=EncryptChat, OU=Dev, O=EncryptChat, L=Tokyo, ST=Tokyo, C=JP"

echo.
echo Done! Keystore created at keystore\release.jks
pause
