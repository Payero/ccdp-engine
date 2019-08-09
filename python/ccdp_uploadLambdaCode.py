# Scott Bennett, scott.bennett@caci.com
# This script allows users to upload their Lambda code to S3 to a location designated
# in the ccdp-config.json file

# The S3 function to upload the code
# src_loc is the path to the file that is to be uploaded to the S3 bucket
#target_loc is the path to where the file should be placed in S3

from optparse import OptionParser
import logging
from pprint import pformat
try:
  import boto3, botocore
except:
  print("Could not find AWS libraries")

import os, sys, traceback
import tarfile, json
from subprocess import call
import shutil
import glob, socket

s3 = boto3.client('s3')

class UploadLambdaCode:

    __LEVELS = {"debug":    logging.DEBUG, 
              "info":     logging.INFO, 
              "warning":  logging.WARN,
              "error":    logging.ERROR}

    __S3_PROT = 's3://'

    def __init__(self, cli_args):
        """
        Initialization of the UploadLambdaCode class
        Sets up logger and calls function to upload a zip file to S3

        :param cli_args: the command line arguments that are passed to the program
        containing source and target locations and target bucket name
        """

        # Members and initialization
        self.__logger = logging.getLogger('UploadLambdaCode')
        handler = logging.StreamHandler()

        log_file = '/tmp/ccdp_install.log'
        if os.path.isfile(log_file):
            os.remove(log_file)

        filelog = logging.FileHandler(log_file)

        formatter = logging.Formatter(
                '%(asctime)s %(name)-12s %(lineno)d %(levelname)-8s %(message)s')
        handler.setFormatter(formatter)
        filelog.setFormatter(formatter)

        self.__logger.addHandler(handler)
        self.__logger.addHandler(filelog)
        
        # Setting root level to warning and THEN set the level for this module
        self.__logger.setLevel(self.__LEVELS['warning'])
        lvl = self.__LEVELS[cli_args.verb_level]
        logging.getLogger('UploadLambdaCode').setLevel(lvl)
        
        self.__logger.debug("Logging Done")

        target_loc = cli_args.tgt_location
        source_loc = cli_args.src_location
        bucket_name = cli_args.bucket
        force_upload = cli_args.force
        #self.__logger.debug("cli_args: " + cli_args)

        # CHECK FOR ARGS VALIDITY
        self.checkValidityOfArgs(target_loc, source_loc, bucket_name, force_upload)

        # Args are valid, do upload
        uploaded = self.uploadZip(target_loc, source_loc, bucket_name)
        if uploaded:
            self.__logger.info("Upload sucessful")
        else:
            self.__logger.error("Upload failed")

    
    # Function for checking the validity of the passed parameters
    def checkValidityOfArgs(self, target_loc, source_loc, bucket_name, force):
        """
        Checks the validity of the arguements provided to the program

        :param target_loc: the location where the file will be uploaded to S3
        :param source_loc: the local location for the desired file to upload
        :param bucket: the name of the bucket to upload the file intos
        """
        
        # Check for valid source name
        if source_loc == None or not os.path.isfile(source_loc):
            self.__logger.error("The source location %s is invalid" % source_loc)
            sys.exit(-1)
        
        # Check that the file is a zip file
        if not source_loc.endswith('.zip'):
            self.__logger.error("The source file %s is not a zip" % source_loc)
            sys.exit(-1)

        # Verify bucket exists
        bucket_list = [bucket['Name'] for bucket in s3.list_buckets()["Buckets"]]
        self.__logger.debug(bucket_list)
        # Note: this only checks first level, not nested buckets
        if not (bucket_name in bucket_list):
            self.__logger.error("The bucket doesn't exist, create the bucket and try again")
            sys.exit(-1)

        # Check if file already exists if force is False
        if not force:
            file_list = [file['Key'] for file in s3.list_objects(Bucket=bucket_name)['Contents']]
            self.__logger.debug("Files in bucket %s: %s", bucket_name, file_list)
            if target_loc in file_list:
                self.__logger.error("The file location already exists in S3; use force flag to overwrite it")
                sys.exit(-1)



    # Connect to S3 and upload the file
    def uploadZip(self, target, source, bucket_name):
        """
        Actually do the upload. Parameters are verified at this point

        :param target: the target location for the file on S3
        :param source: the source location for the file on the local machine
        :param bucket_name: the name of the bucket to put the file in on S3
        """

        try:
            response = s3.upload_file(source, bucket_name, target)
        except botocore.exceptions.ClientError as e:
            self.__logger.error("Error Uploading File")
            self.__logger.error(e)
            return False
        return True

if __name__ == "__main__":
    import textwrap

    #lines = textwrap.dedent(UploadLambdaCode.__doc__).splitlines()
    
    help_text = "Help here"
    #help_text = "\n".join(lines)
    # for line in lines:
    #   help_text += "\n%s" % line

    parser = OptionParser(usage=help_text, version="%prog 1.0")
    
    parser.add_option('-v', '--verbosity-level',
            type='choice',
            action='store',
            dest='verb_level',
            choices=['debug', 'info', 'warning','error',],
            default='debug',
            help='The verbosity level of the logging')

    parser.add_option('-t', '--target-location',
            action='store',
            dest='tgt_location',
            default=None,
            help='The location where files needs to be uploaded')

    parser.add_option("-s", "--source-location",
            dest="src_location",
            default=None,
            help="The local location of the file to be uploaded")

    parser.add_option("-b", "--bucket",
            dest="bucket", 
            default=None,
            help="The bucket that the file will be placed into")  
    
    parser.add_option("-f", "--force",
            dest="force",
            action="store_true",
            default=False,
            help="Force an existing file on S3 to be overwritten")

    (options, args) = parser.parse_args()
    
    UploadLambdaCode(options)