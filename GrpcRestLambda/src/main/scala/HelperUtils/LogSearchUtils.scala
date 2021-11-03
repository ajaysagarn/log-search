package HelperUtils

import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, ListObjectsV2Request, S3Object}

import java.io.InputStream
import scala.util.matching.Regex

/**
 * This class consists of several util function that implement reusable logic for the lambda function.
 */
class LogSearchUtils
object LogSearchUtils {
  val config = ObtainConfigReference("application.conf","CONFIG").get
  val logger = CreateLogger(classOf[LogSearchUtils])

  //S3 client to communicate with AWS s3 service
  val s3: S3Client = S3Client.builder().region(Region.US_WEST_2)
    .httpClient(ApacheHttpClient.builder().build())
    .build()

  /**
   * Get the input stream from the key location from the bucket specified
   * @param bucket
   * @param key
   * @return - Input stream
   */
  def getS3InputStream(bucket:String, key:String): InputStream = {
    logger.info("Creating s3 object request")
    val objrequest: GetObjectRequest = GetObjectRequest.builder()
      .bucket(bucket).key(key)
      .build()

    logger.info("s3 object request created")

    logger.info("Reading input stream from log file in s3")
    s3.getObjectAsBytes(objrequest).asInputStream()

  }

  /**
   * Stores the inpustream received from an s3 bucket into a scala List
   * @param logs
   * @param file
   * @return
   */
  def getLogsListFromS3(logs: List[String] , file: Iterator[String]): List[String] = {
    if(!file.hasNext){
      return logs
    }
    val log = file.next()
    val newList: List[String] = logs:::List(log)
    if(getLogTimeStamp(log).nonEmpty){
      getLogsListFromS3(newList,file)
    }else{
      getLogsListFromS3(logs,file)
    }
  }

  /**
   * Retreive the timestamp string from the Log String
   * Uses a regex pattern to get the required timestamp
   * @param message
   * @return
   */
  def getLogTimeStamp(message:String): Option[String] = {
    // Regex pattern to match the time stamp in a log string
    val pattern = new Regex("[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}")
    pattern.findFirstIn(message)
  }


  /**
   * Return the hours value from the log timestamp
   * @param timestamp
   * @return
   */
  def getLogHours(timestamp:String): Option[Integer] = {
    val parts: Array[String] = timestamp.split(':')

     if(parts.length > 0){
        Option.apply[Integer](Integer.parseInt(parts(0)))
      }else{
       Option.empty[Integer]
     }
  }

  /**
   * Return the minutes value from the log timestamp
   * @param timestamp
   * @return
   */
  def getLogMinutes(timestamp:String): Option[Integer] = {
    val parts: Array[String] = timestamp.split(':')

    if(parts.length > 1){
      Option.apply[Integer](Integer.parseInt(parts(1)))
    }else{
      Option.empty[Integer]
    }
  }

  /**
   * Checks the number of minutes elapsed from 00:00 for the given timestamp
   * @param timestamp
   * @return
   */
  def getLogMinutesElasped(timestamp:String): Option[Integer] = {
    val hours = getLogHours(timestamp)
    val minutes = getLogMinutes(timestamp)

    if(hours.nonEmpty && minutes.nonEmpty){
      Option.apply((hours.get * 60)+ minutes.get)
    }else{
      Option.empty
    }
  }

  /**
   * Check if the json inpt sent to the REST lambda has the required attributes
   * @param reqPayload
   * @return
   */
  def isValidInput(reqPayload: Map[String,Any]): Boolean = {
    if(reqPayload.contains("type") && reqPayload.contains("input")){
      val inputVals:Map[String,Any] = reqPayload.get("input").get.asInstanceOf[Map[String,Any]]
      if(inputVals.contains("st") && inputVals.contains("et")){
        true
      }else{
        false
      }
    }else{
      false
    }
  }

  /**
   * Retreive the message part of the log
   * @param messages
   * @param pattern
   * @return - String - message
   */
  def getMatchingMessages(messages:List[String], pattern:String): List[String] = {
    List.empty[String]
  }

  /**
   * Get the file name for the first file present under the given s3 bucket.
   * This project is set up in a that makes sure that the s3 bucket has an input folder that always contains one log file
   * being update with new logs evey 30 minutes. Here we simple get the name of the current log file.
   * @param bucket
   * @return
   */
  def getLogFileKey(bucket:String):Option[S3Object] = {
    val listObjectsRequest = ListObjectsV2Request.builder.bucket(config.getString("S3_BUCKET")).prefix(config.getString("FOLDER")).build
    val listing = s3.listObjectsV2(listObjectsRequest)
    if(listing.hasContents)
      Option.apply(listing.contents().get(0)) // get the first log file..Will always have one file
    else
      Option.empty
  }

  /**
   * Perform binary search on a List of Logs to check if the logs contain timestamps within the given time intervals
   * @param start
   * @param end
   * @param logs
   * @return
   */
  def checkTimeIntervalExistsBS(start:String, end:String, logs: List[String]): Int ={
    val sm = LogSearchUtils.getLogMinutesElasped(start)
    val em = LogSearchUtils.getLogMinutesElasped(end)
    defBinarySearchLogsRec(0,logs.size-1,sm.get,em.get,logs)
  }

  /**
   * Recursive binary search on the list of log files
   * @param min - The min value
   * @param max - The max value
   * @param start - The starting time stamp
   * @param end - The ending timestamp
   * @param logs - List of logs to be searched
   * @return - The index where a log within the time range is found. -1 if no logs within the time range are found.
   */
  def defBinarySearchLogsRec(min:Integer,max:Integer,start:Integer,end:Integer,logs:List[String]): Int ={
    val mid = min+max/2

    // check for terminating conditions
    if((min > max) || (mid < 0) || (mid> logs.size -1)){
      return -1
    }

    val midLog = logs(mid)
    val midTime = getLogMinutesElasped(getLogTimeStamp(midLog).get).get

    if(midTime >= start && midTime <=end){
      return mid
    }else if(midTime < start){
      return defBinarySearchLogsRec(mid+1,max,start,end,logs)
    } else if(midTime > end){
      return defBinarySearchLogsRec(min,mid-1,start,end,logs)
    }
    -1
  }

  /**
   * Here input a pivot index- the index where an item within the range was found using binary search.
   * Using the pivot index we spread out on eother side of the list until we are out pf the time range specified.
   * While fetching the logs within the time interval, we check for the pattern in the message and construc a list of those messages to
   * be retured
   * @param logs - List of logs to besearched
   * @param pivot - The pivot index from where the logs need to be searched on either side to get the logs within the time range
   * @param start - The starting time range
   * @param end - The ending time range
   * @param pattern - The pattern that needs to be matched against
   * @return - A list of all messages that match the given pattern in the list of logs provided
   */
  def getMatchingLogsInRange(logs:List[String], pivot:Integer, start:String, end:String, pattern:String):List[String]={
    val st = getLogMinutesElasped(start).get
    val et = getLogMinutesElasped(end).get
    getLogsBeforePivot(logs,pivot,st,List.empty[String],pattern):::getLogsBeforePivot(logs,pivot+1,et,List.empty[String],pattern)
  }

  /**
   * Recursively check for logs before the pivot that are within the time range and check of they match the given pattern
   * @param logs
   * @param pivot
   * @param start
   * @param rangeList
   * @param pattern
   * @return
   */
  def getLogsBeforePivot(logs:List[String],pivot:Integer,start:Integer,rangeList:List[String], pattern:String):List[String] = {
    if(pivot < 0 || pivot > logs.size-1){
      return rangeList
    }
    val log = logs(pivot)
    if(getLogMinutesElasped(getLogTimeStamp(log).get).get < start){
      return rangeList
    }

    if(doesContainPattern(getLogMessage(log),pattern.r)){
      getLogsBeforePivot(logs,pivot-1,start,rangeList:::List(getLogMessage(log)),pattern)
    }else{
      getLogsBeforePivot(logs,pivot-1,start,rangeList,pattern)
    }

  }

  /**
   * Recursively check for logs before the pivot that are within the time range and check of they match the given pattern
   * @param logs
   * @param pivot
   * @param end
   * @param rangeList
   * @param pattern
   * @return
   */
  def getLogsAfterPivot(logs:List[String],pivot:Integer,end:Integer,rangeList:List[String],pattern:String):List[String] = {
    if(pivot < 0 || pivot > logs.size-1){
      return rangeList
    }
    val log = logs(pivot)
    if(getLogMinutesElasped(getLogTimeStamp(log).get).get > end){
      return rangeList
    }

    if(doesContainPattern(getLogMessage(log),pattern.r)){
      getLogsBeforePivot(logs,pivot+1,end,rangeList:::List(getLogMessage(log)),pattern)
    }else{
      getLogsBeforePivot(logs,pivot+1,end,rangeList,pattern)
    }
  }


  /**
   * Get the message part of the log string
   * @param log
   * @return
   */
  def getLogMessage(log: String): String = {
    val parts: Array[String] = log.split(" - ").map(str => str.trim())
    val message = parts(parts.length -1)
    message
  }

  /**
   * Check if the given string consists the given regex pattern in it
   * @param message
   * @param pattern
   * @return - Boolean
   */
  def doesContainPattern(message:String, pattern:Regex): Boolean = {
    !pattern.findFirstIn(message).isEmpty
  }

  /**
   * Check if the S3 input stream has logs within the given time range.
   * @param start - Start time
   * @param end - end time
   * @param file - S3 file iterator
   * @param getMessages - specify if all the messages in the time range need to be returned
   * @param logsInRange - the list of messages within the time range - will have only one message if getMessages is false
   * @return - List of messages that are present within the given time range
   */
  def checkTimeIntervalExists(start:String, end:String, file: Iterator[String], getMessages:Boolean, logsInRange: List[String] ): List[String] ={
    //print("Started Time interval check")
    if(!file.hasNext){
      return logsInRange
    }

    val log = file.next()
    val tm = LogSearchUtils.getLogMinutesElasped(log)
    val sm = LogSearchUtils.getLogMinutesElasped(start)
    val em = LogSearchUtils.getLogMinutesElasped(end)

    if(tm.nonEmpty && tm.get >= sm.get && tm.get <= em.get){
      if(!getMessages){
        return logsInRange ::: List(log)
      }
      checkTimeIntervalExists(start,end,file,getMessages,logsInRange ::: List(log))
    }else if(tm.get > em.get){
      logsInRange
    }else{
      checkTimeIntervalExists(start,end,file,getMessages,logsInRange)
    }

  }


}
