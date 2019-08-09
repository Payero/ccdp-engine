# Scott Bennett, scott.bennett@caci.com
# A python script for learning how to interact with AWS S3 using boto3

from boto3 import client
import json

config_fp = '/projects/users/srbenne/workspace/engine/config/ccdp-config.json'
s3 = client('s3')

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

if __name__ == '__main__':
    #ListS3Dir()
    #DownloadS3File()
    loadConfig()