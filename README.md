# The Runtime Governance UI Project

## Summary

This is the official Git repository for the Runtime Governance UI project.

## Get the code

The easiest way to get started with the code is to [create your own fork](http://help.github.com/forking/) of this repository, and then clone your fork:

	$ git clone git@github.com:<you>/rtgov-ui.git
	$ cd rtgov-ui
	$ git remote add upstream git://github.com/Governance/rtgov-ui.git
	
At any time, you can pull changes from the upstream and merge them onto your master:

	$ git checkout master               # switches to the 'master' branch
	$ git pull upstream master          # fetches all 'upstream' changes and merges 'upstream/master' onto your 'master' branch
	$ git push origin                   # pushes all the updates to your fork, which should be in-sync with 'upstream'

The general idea is to keep your 'master' branch in-sync with the 'upstream/master'.

## Building UI

We use Maven 3.x to build our software. The following command compiles all the code, installs the JARs into your local Maven repository, and runs all of the unit tests:

	$ mvn clean install
	
## Running UI

The Runtime Governance UI project builds as a WAR which can be deployed to a Java application server such as JBoss EAP.
In fact, a specific EAP 6.1 version of the WAR is created during the build process.

Another (even easier) way to run the rtgov-ui project is to simply do this:

    $ mvn -Prun clean install

The "run" profile will be activated, which will launch the application using an embedded Jetty server.

## Running JBoss Integration Tests

First step is to setup a server environment. Download EAP 6.1 and install the latest version of SwitchYard.

Set the JBOSS_HOME environment variable to the home directory of the EAP/SwitchYard installation.

Then run the following from the _tests_ folder:

	$ mvn clean install

## Deploying to JBoss EAP

Once a full build has been performed using:

	$ mvn clean install

from the top level folder, deploy the $rtgov-ui/overlord-rtgov-ui-war-eap6/target/overlord-rtgov-ui.war file into the EAP standalone/deployments folder.

## Contribute fixes and features

This project is open source, and we welcome anybody who wants to participate and contribute!

If you want to fix a bug or make any changes, please make the changes on a reasonably named topic branch. For example, this command creates
a branch for a UI module bug fix:

	$ git checkout -b foo-problem-in-UI

After you're happy with your changes and a full build (with unit tests) runs successfully, commit your changes on your topic branch
(using [really good comments](http://community.jboss.org/wiki/OverlordDevelopmentGuidelines#Commits)). Then it's time to check for
and pull any recent changes that were made in the official repository:

	$ git checkout master                           # switches to the 'master' branch
	$ git pull upstream master                      # fetches all 'upstream' changes and merges 'upstream/master' onto your 'master' branch
	$ git checkout foo-problem-in-UI     		# switches to your topic branch
	$ git rebase master                             # reapplies your changes on top of the latest in master
	                                                  (i.e., the latest from master will be the new base for your changes)

If the pull grabbed a lot of changes, you should rerun your build to make sure your changes are still good.
You can then push your topic branch and its changes into your public fork repository

	$ git push origin foo-problem-in-UI         	# pushes your topic branch into your public fork

and [generate a pull-request](http://help.github.com/pull-requests/) for your changes. 

We prefer pull-requests, because we can review the proposed changes, comment on them,
discuss them with you, and likely merge the changes right into the official repository.
