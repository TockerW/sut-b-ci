# Запуск з папки perf. Потрібен jmeter у PATH.
$env:PGPASSWORD = "password"
$env:Path += ";C:\Program Files\PostgreSQL\18\bin"
$env:Path += ";D:\apache-jmeter-5.6.3\bin"

function Run([string]$name, [string]$threads, [string]$rampup, [string]$duration, [string]$think) {
  psql -h localhost -U courses_student -d courses_variant14_test -c "TRUNCATE students, courses, enrollments CASCADE"
  Remove-Item -Recurse -Force "report-jmeter-$name", "results-jmeter-$name.jtl" -ErrorAction SilentlyContinue

  $jmArgs = @(
    "-n", "-t", "lab7.jmx",
    "-l", "results-jmeter-$name.jtl",
    "-e", "-o", "report-jmeter-$name",
    "-Jthreads=$threads",
    "-Jrampup=$rampup",
    "-Jduration=$duration",
    "-Jthink=$think"
  )
  Write-Host ">>> $name : jmeter $($jmArgs -join ' ')"
  & jmeter @jmArgs
}

Run "baseline" "1"   "0"   "60"  "1000"
Run "load"     "20"  "30"  "240" "1000"
Run "stress"   "200" "240" "270" "100"
