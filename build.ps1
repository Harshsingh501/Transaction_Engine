$MVN = "$env:USERPROFILE\AppData\Local\Programs\IntelliJ IDEA Community Edition 2025.2.6.1\plugins\maven\lib\maven3\bin\mvn.cmd"
$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21"
& $MVN clean package "-DskipTests" 2>&1
