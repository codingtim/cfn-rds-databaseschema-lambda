# rds database schema Lambda

Cloudformation custom resource to create a schema for a PostgreSQL database. 

1. run `mvn clean install`
2. upload shaded jar to a S3 bucket
3. create stack for the custom resource `custom-resource-stack.json` in private subnet with NAT Gateway
4. create stack that uses the custom resource `rds-schema.json`

## resources

