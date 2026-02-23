$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21"
& "$env:JAVA_HOME\bin\java.exe" "-jar" "target\transaction-engine-jar-with-dependencies.jar" "trades.csv"
