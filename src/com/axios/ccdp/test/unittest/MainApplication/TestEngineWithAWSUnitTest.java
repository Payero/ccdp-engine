package com.axios.ccdp.test.unittest.MainApplication;

import org.junit.BeforeClass;

import com.axios.ccdp.utils.CcdpUtils;

public class TestEngineWithAWSUnitTest extends CcdpMainApplicationTests
{

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		CcdpVMcontroller = "com.axios.ccdp.cloud.aws.AWSCcdpVMControllerImpl";
		CcdpVMStorageController = "com.axios.ccdp.cloud.aws.AWSCcdpStorageControllerImpl";
		ClassMonitorIntf = "com.axios.ccdp.utils.LinuxResourceMonitorImpl";
		addSecond = 20;


		//setting image id and command for the vm or container
		CcdpUtils.setProperty("resourceIntf.default.image.id", "ami-00960e391c6790e70");
		CcdpUtils.setProperty("resourceIntf.default.startup.command", "data/ccdp/ccdp_install.py -a download -d s3://ccdp-settings/ccdp-engine.tgz -w -t /data/ccdp -n DEFAULT");
		CcdpUtils.setProperty("resourceIntf.ec2.image.id", "ami-00960e391c6790e70");
		CcdpUtils.setProperty("resourceIntf.ec2.startup.command", "data/ccdp/ccdp_install.py -a download -d s3://ccdp-settings/ccdp-engine.tgz -w -t /data/ccdp -n EC2");
		CcdpUtils.setProperty("resourceIntf.nifi.image.id", "ami-075cb764a295e450f");
		CcdpUtils.setProperty("resourceIntf.nifi.startup.command", "data/ccdp/ccdp_install.py -a download -d s3://ccdp-settings/ccdp-engine.tgz -w -t /data/ccdp -n NIFI");
	}

}
