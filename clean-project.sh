#!/bin/bash

echo "==================================================="
echo "= Android Project Deep Clean Script                ="
echo "==================================================="
echo

# Проверка наличия gradlew
if [ ! -f "./gradlew" ]; then
    echo "ERROR: gradlew not found!"
    echo "Please run this script from the project root directory."
    exit 1
fi

# Убедимся, что gradlew имеет права на выполнение
chmod +x ./gradlew

echo "Select cleanup mode:"
echo
echo "1. Standard Clean (safe, with running Gradle)"
echo "2. Generate Full Cleanup Scripts (to run when Android Studio is closed)"
echo "3. Exit"
echo

read -p "Enter your choice (1-3): " mode

if [ "$mode" = "1" ]; then
    echo
    echo "Starting standard clean process..."
    echo

    ./gradlew -b clean.gradle cleanAll

    if [ $? -ne 0 ]; then
        echo
        echo "WARNING: Some files could not be deleted because they are in use."
        echo "For a complete cleanup, close Android Studio and run the Full Cleanup Script."
        echo
        read -p "Would you like to generate Full Cleanup Scripts now? (y/N) " generate
        
        if [[ "$generate" =~ ^[Yy]$ ]]; then
            echo
            echo "Generating Full Cleanup Scripts..."
            ./gradlew -b clean.gradle generateFullCleanScript
        fi
    else
        echo
        echo "==================================================="
        echo "= Clean completed successfully!                   ="
        echo "==================================================="
    fi
elif [ "$mode" = "2" ]; then
    echo
    echo "Generating Full Cleanup Scripts..."
    echo
    
    ./gradlew -b clean.gradle generateFullCleanScript
    
    # Сделаем скрипт исполняемым
    chmod +x ./full-clean.sh
    
    echo
    echo "Scripts generated! Run them when Android Studio is closed."
elif [ "$mode" = "3" ]; then
    echo "Exiting..."
    exit 0
else
    echo "Invalid choice. Please enter 1, 2, or 3."
    exit 1
fi

echo
echo "You can now rebuild your project with './gradlew build'"

exit 0 