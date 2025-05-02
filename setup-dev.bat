@echo off
echo Настройка среды разработки для проекта Alan AI Assistant
echo.

set PROJECT_ROOT=%~dp0
set SECRETS_FILE=%PROJECT_ROOT%secrets.properties
set DEV_SECRETS_FILE=%PROJECT_ROOT%secrets.properties.dev

echo Проверка наличия файла secrets.properties...

if exist "%SECRETS_FILE%" (
  echo.
  echo Файл secrets.properties уже существует.
  echo Если вы хотите использовать другие API ключи, отредактируйте файл напрямую.
  echo.
) else (
  echo Файл secrets.properties не найден.
  echo.
  
  if exist "%DEV_SECRETS_FILE%" (
    echo Найден файл secrets.properties.dev
    echo Копирую его в secrets.properties для использования демо-ключей...
    copy "%DEV_SECRETS_FILE%" "%SECRETS_FILE%" > nul
    echo.
    echo Файл secrets.properties создан с демо-значениями.
    echo ВНИМАНИЕ: Демо-ключи имеют ограничения. Для полноценной работы замените их на собственные.
  ) else (
    echo Создаю новый файл secrets.properties...
    echo # Ключи API для приложения Alan AI Assistant > "%SECRETS_FILE%"
    echo # Этот файл НЕ должен попадать в систему контроля версий >> "%SECRETS_FILE%"
    echo. >> "%SECRETS_FILE%"
    echo # OpenRouter API key >> "%SECRETS_FILE%"
    echo OPENROUTER_API_KEY=your_openrouter_api_key_here >> "%SECRETS_FILE%"
    echo. >> "%SECRETS_FILE%"
    echo # Weather API key (OpenWeatherMap) >> "%SECRETS_FILE%"
    echo WEATHER_API_KEY=your_weather_api_key_here >> "%SECRETS_FILE%"
    echo.
    echo Файл secrets.properties создан с заполнителями.
    echo Пожалуйста, отредактируйте его и замените заполнители на ваши реальные API ключи.
  )
)

echo.
echo Настройка завершена.
echo Пожалуйста, убедитесь, что secrets.properties содержит действительные API ключи.
echo.
pause 