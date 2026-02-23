$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21"
$mvnZip = "$env:TEMP\maven.zip"
$mvnDir = "$env:TEMP\maven"

Write-Host "Downloading Apache Maven 3.9.6..."
Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip" -OutFile $mvnZip -UseBasicParsing
Write-Host "Extracting..."
Expand-Archive -Force -Path $mvnZip -DestinationPath $mvnDir
Write-Host "Maven installed."
& "$mvnDir\apache-maven-3.9.6\bin\mvn.cmd" "--version"
