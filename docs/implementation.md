### Project Architecture

![image](./architecture.PNG)

The Figure above illustrates the implemented architecture of this project.

The implementation consists of the localhost AKKA service that exposes Rest apis and redirects calls to the AWS api gateway.
The AWS API gateway further invokes a lambda function that executes the business logic to determine if logs are present within a specified timespan and returns the appropriate response.
The Lambda functions has access to S3 buckets from where logs are Fetched. The Logs themselves are continuously generated and uploaded to s3 by a LogGenerator deployed on an EC2 instance where a cron job is configures to run the log generator every 30 minutes.

Grpc is used on the localhost client as well as in the lambda function inorder to handle GRPC style requests.

In the Akka service, an implementation of the generated GRPC class is created [GrpcService](../AkkaService/src/main/scala/com/ajsa/service/GrpcService.scala), that abstracts the functionality to be performed within the GRPC service.
This class simply receives the grpc request object and send the appropriate request to the API Gateway and responds with the corresponding GPRC response object.

Requests sent the API gateway in the GRPC format are first marshalled into JSON before they are sent.

On the Lambda function, The JSON input received is checked to see if it's a GRPC request or and if so, the JSON input received in the lambda is again unmarshalled into the corresponding GRPC class object before sending it to the server side GRPC class implementation that handles the GRPC request.

### EC2 Log Generation.

The [Log Generator](https://github.com/ajaysagarn/LogFileGenerator) is deployed to an EC2 instance. A [build script](https://github.com/ajaysagarn/LogFileGenerator/blob/main/build.sh) has been added to the log generator which consists of the commands that need to be executed inorder to run the log generator as well as to move the log file from the ec2 instace to the s3 bucket using the aws CLI.

