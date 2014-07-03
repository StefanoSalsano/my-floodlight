My Floodlight
==================

Floodlight implementation for Conet, based on 0.90.

Usage
================

- Select the branch (0.90 or RPF)
		~my-floodlight$ git checkout 0.90

- To compile run ant in my-floodlight/ folder
		~my-floodlight$ ./ant

- Modify conetcontroller.conf according to your topology. In particular you have to modify the sw_datapth and homeserver_range entries, these data are needed for TBFF policy, for example

		######## Ofelia Topologies ########
		# SwitchTrento#3 configuration
		sw_datapath=02:08:02:08:00:00:00:03
		homeserver_range=192.168.64.1/32

		# SwitchZurich#1 configuration
		sw_datapath=02:00:00:00:00:00:00:01
		homeserver_range=192.168.64.2/32

		# SwitchBarcelona#1 configuration
		sw_datapath=00:10:00:00:00:00:00:03
		homeserver_range=192.168.64.3/32

		# SwitchBerlin#1 configuration
		sw_datapath=00:00:00:00:00:00:04:01
		homeserver_range=192.168.64.4/32

- Finally start the controller running start.sh script. After some debug print you should able to see the connections of OpenFlow switches and of Cache Server.
		~my-floodlight$ ./start.sh
