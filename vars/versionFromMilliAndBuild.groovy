import java.time.Instant
import java.time.ZoneOffset

@NonCPS
String call(Long epochMilli, Integer buildNumber) {
  def when = Instant.ofEpochMilli(epochMilli).atOffset(ZoneOffset.UTC)
  "${when.year}.${when.month}.${when.dayOfMonth}-${buildNumber}".toString()
}
