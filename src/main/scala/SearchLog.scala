
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger, RequestHandler}

class SearchLog extends RequestHandler[Map[String,String],String] {
  override def handleRequest(input: Map[String, String], context: Context): String = {
    val logger: LambdaLogger = context.getLogger
    val res:String = new String("200 OK")
    logger.log("Hello World")
    return res
  }
}
