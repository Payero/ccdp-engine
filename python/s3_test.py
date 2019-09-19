#!/usr/bin/env python3

# Scott Bennett, scott.bennett@caci.com
# A python script for learning how to interact with AWS S3 using boto3

from boto3 import client
import boto3
import json, os

config_fp = '/projects/users/srbenne/workspace/engine/config/ccdp-config.json'
s3 = client('s3')
__S3_PROT = 's3://'


def ListS3Dir():
    print("\nPrinting the contents of S3 Bucket 'ccdp-tasks':\n")
    for key in s3.list_objects(Bucket='ccdp-tasks')['Contents']:
        print(key['Key'])

def DownloadS3File():
    print("\nDownloading S3 Item 'pi.out'\n ")
    s3.download_file('ccdp-tasks', 'pi.out', '/nishome/srbenne/downloadedpi.out')

def loadConfig():
    with open(config_fp) as file:
        config = json.load(file)
    print(json.dumps(config, indent=2))

def uploadTest(src_file, tgt_loc):
      
    # Do we have a valid source file?
    if src_file is None or not os.path.isfile(src_file):
        print("The source file %s is invalid" % src_file)
        return None

    filename = 'bogus_filename'
    path, fname = os.path.split(src_file)

    # getting the file from a S3 bucket
    if tgt_loc.startswith(__S3_PROT):
        print("Uploading file into S3 bucket")
        s3 = boto3.resource('s3')

        if tgt_loc.endswith('/'):
          tgt_loc = tgt_loc[:-1]

        bkt_name = tgt_loc[len(__S3_PROT):]


        # Creating a bucket
        try:
          #self.__s3.create_bucket(Bucket=bkt_name, CreateBucketConfiguration={
          #                        'LocationConstraint': 'us-east-1'})  
          s3.create_bucket(Bucket=bkt_name)
          print("Bucket %s was created" % bkt_name)
        except:
          print("Bucket (%s) already exists" % bkt_name) 
        
        bkt = s3.Bucket(bkt_name)
        # Storing data
        s3.Object(bkt_name, fname).put(
                          Body=open(src_file, 'rb'))
        print("File %s was uploaded successfully" % fname)
        
        acl = bkt.Acl()
        bkt.Acl().put(ACL='public-read')   

        return fname

if __name__ == '__main__':
    #ListS3Dir()
    #DownloadS3File()
    #loadConfig()
    uploadTest("/nishome/srbenne/pi.out", "s3://ccdp-settings/folder/")
    
    