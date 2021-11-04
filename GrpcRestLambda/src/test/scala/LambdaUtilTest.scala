
import HelperUtils.{LogSearchUtils}
import org.scalatest.PrivateMethodTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.Console.in

class LambdaUtilTest extends AnyFlatSpec with Matchers with PrivateMethodTester{

  behavior of "LogSearchUtils"

  "getLogMessage" should "Return the correct log message from the log input" in {
    val msg1 = "16:54:04.774 [scala-execution-context-global-25] ERROR HelperUtils.Parameters$ - s%]s,+2k|D}K7b/XCwG&@7HDPR8z"
    val msg2 = "16:54:05.301 [scala-execution-context-global-25] DEBUG HelperUtils.Parameters$ - JrQB;P0\"&+6;&Dk-"
    val ans1 = LogSearchUtils.getLogMessage(msg1)
    val ans2 = LogSearchUtils.getLogMessage(msg2)

    assert(ans1 == "s%]s,+2k|D}K7b/XCwG&@7HDPR8z")
    assert(ans2 == "JrQB;P0\"&+6;&Dk-")
  }


  "getLogTimeStamp" should "return timestamp string from the log message" in {
    val msg1 = "22:23:19.384 [scala-execution-context-global-25] ERROR HelperUtils.Parameters$ - N&I3aq7Wae3A9fQ5ice1V5k~=R{s6ng"
    val msg2 = "16:54:05.301 [scala-execution-context-global-25] DEBUG HelperUtils.Parameters$ - JrQB;P0\"&+6;&Dk-"

    val ans1 = LogSearchUtils.getLogTimeStamp(msg1)
    val ans2 = LogSearchUtils.getLogTimeStamp(msg2)

    assert(ans1.get == "22:23:19.384")
    assert(ans2.get == "16:54:05.301")
  }

  "getLogTimeElapsed" should "calcuate the correct amount of minutes elasped from 00:00" in {
    val msg1 = "22:23:19.384 [scala-execution-context-global-25] ERROR HelperUtils.Parameters$ - N&I3aq7Wae3A9fQ5ice1V5k~=R{s6ng"
    val msg2 = "16:54:05.301 [scala-execution-context-global-25] DEBUG HelperUtils.Parameters$ - JrQB;P0\"&+6;&Dk-"

    val ans1 = LogSearchUtils.getLogMinutesElasped(LogSearchUtils.getLogTimeStamp(msg1).get).get
    val ans2 = LogSearchUtils.getLogMinutesElasped(LogSearchUtils.getLogTimeStamp(msg2).get).get

    assert (ans1 == 1343)
    assert(ans2 == 1014)

  }


  "doesContainPattern" should "check if the given log message had the pattern specified" in {
    val pattern = "([a-c][e-g][0-3]|[A-Z][5-9][f-w]){5,15}"

    val msg1 = "22:23:19.384 [scala-execution-context-global-25] ERROR HelperUtils.Parameters$ - N&I3aq7Wae3A9fQ5ice1V5k~=R{s6ng"
    val msg2 = "16:54:05.301 [scala-execution-context-global-25] DEBUG HelperUtils.Parameters$ - JrQB;P0\"&+6;&Dk-"

    val ans1 = LogSearchUtils.doesContainPattern(msg1,pattern.r)
    val ans2 = LogSearchUtils.doesContainPattern(msg2,pattern.r)

    assert (ans1 == true)
    assert(ans2 == false)

  }


  "getLogMinutes" should "return the minutes part of a log timestamp" in {
    val first = LogSearchUtils.getLogMinutes("00:45:33")
    val second = LogSearchUtils.getLogMinutes(("23:32:44"))
    val third = LogSearchUtils.getLogMinutes("15:59")


    assert(first.get == 45)
    assert(second.get == 32)
    assert(third.get == 59)

  }

  "getLogHours" should "return the hours part of a log timestamp" in {
    val first = LogSearchUtils.getLogHours("00:45:33")
    val second = LogSearchUtils.getLogHours(("23:32:44"))
    val third = LogSearchUtils.getLogHours("15:59")


    assert(first.get == 0)
    assert(second.get == 23)
    assert(third.get == 15)

  }







}
