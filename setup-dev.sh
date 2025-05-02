#!/bin/bash

echo "Настройка среды разработки для проекта Alan AI Assistant"
echo

PROJECT_ROOT=$(dirname "$0")/
SECRETS_FILE="${PROJECT_ROOT}secrets.properties"
DEV_SECRETS_FILE="${PROJECT_ROOT}secrets.properties.dev"

echo "Проверка наличия файла secrets.properties..."

if [ -f "$SECRETS_FILE" ]; then
  echo
  echo "Файл secrets.properties уже существует."
  echo "Если вы хотите использовать другие API ключи, отредактируйте файл напрямую."
  echo
else
  echo "Файл secrets.properties не найден."
  echo
  
  if [ -f "$DEV_SECRETS_FILE" ]; then
    echo "Найден файл secrets.properties.dev"
    echo "Копирую его в secrets.properties для использования демо-ключей..."
    cp "$DEV_SECRETS_FILE" "$SECRETS_FILE"
    echo
    echo "Файл secrets.properties создан с демо-значениями."
    echo "ВНИМАНИЕ: Демо-ключи имеют ограничения. Для полноценной работы замените их на собственные."
  else
    echo "Создаю новый файл secrets.properties..."
    cat > "$SECRETS_FILE" << EOF
# Ключи API для приложения Alan AI Assistant
# Этот файл НЕ должен попадать в систему контроля версий

# OpenRouter API key
OPENROUTER_API_KEY=your_openrouter_api_key_here

# Weather API key (OpenWeatherMap)
WEATHER_API_KEY=your_weather_api_key_here
EOF
    echo
    echo "Файл secrets.properties создан с заполнителями."
    echo "Пожалуйста, отредактируйте его и замените заполнители на ваши реальные API ключи."
  fi
fi

echo
echo "Настройка завершена."
echo "Пожалуйста, убедитесь, что secrets.properties содержит действительные API ключи."
echo

# Делаем скрипт исполняемым
chmod +x "$0" 