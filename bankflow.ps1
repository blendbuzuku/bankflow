Set-Location "C:\Users\BV2\Documents\GitHub\bankflow"

# Load environment variables
. .\start-env.ps1

function Start-AuthService {
    Set-Location "C:\Users\BV2\Documents\GitHub\bankflow\backend\auth-service"
    .\mvnw.cmd spring-boot:run
}

function Start-AccountService {
    Set-Location "C:\Users\BV2\Documents\GitHub\bankflow\backend\account-service"
    .\mvnw.cmd spring-boot:run
}

function Start-TransactionService {
    Set-Location "C:\Users\BV2\Documents\GitHub\bankflow\backend\transaction-service"
    .\mvnw.cmd spring-boot:run
}

function Start-Frontend {
    Set-Location "C:\Users\BV2\Documents\GitHub\bankflow\frontend"
    npm start
}

while ($true) {

    Clear-Host

    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host "           BANKFLOW DEVELOPMENT             " -ForegroundColor Cyan
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Environment: " -NoNewline
    Write-Host "LOADED" -ForegroundColor Green
    Write-Host "Database:    $env:DB_URL"
    Write-Host "JWT:         LOADED"
    Write-Host ""
    Write-Host "--------------------------------------------"
    Write-Host "  1. Start Auth Service        :8081"
    Write-Host "  2. Start Account Service     :8080"
    Write-Host "  3. Start Transaction Service :8082"
    Write-Host "  4. Start Frontend            :4200"
    Write-Host "  5. Open Project Folder"
    Write-Host "  6. Exit"
    Write-Host "--------------------------------------------"
    Write-Host ""

    $choice = Read-Host "Select an option"

    switch ($choice) {

        "1" {
            Write-Host ""
            Write-Host "Starting Auth Service..." -ForegroundColor Green
            Start-AuthService
        }

        "2" {
            Write-Host ""
            Write-Host "Starting Account Service..." -ForegroundColor Green
            Start-AccountService
        }

        "3" {
            Write-Host ""
            Write-Host "Starting Transaction Service..." -ForegroundColor Green
            Start-TransactionService
        }

        "4" {
            Write-Host ""
            Write-Host "Starting Frontend..." -ForegroundColor Green
            Start-Frontend
        }

        "5" {
            explorer "C:\Users\BV2\Documents\GitHub\bankflow"
        }

        "6" {
            Write-Host "Goodbye!" -ForegroundColor Cyan
            break
        }

        default {
            Write-Host ""
            Write-Host "Invalid option. Please select 1-6." -ForegroundColor Red
            Start-Sleep -Seconds 2
        }
    }
}