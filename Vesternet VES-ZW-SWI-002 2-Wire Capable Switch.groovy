/**
 *	Vesternet VES-ZW-SWI-002 2-Wire Capable Switch
 * 
 */
import groovy.json.JsonOutput
metadata {	
	definition (name: "Vesternet VES-ZW-SWI-002 2-Wire Capable Switch", namespace: "Vesternet", Vesternet: "Sunricher", mcdSync: true, ocfDeviceType: "oic.d.switch", mnmn: "SmartThings", vid: "generic-switch") {
		capability "Switch"
		capability "Actuator"
		capability "Sensor"
		capability "Refresh"		
		capability "Configuration"

		fingerprint mfr: "0330", prod: "0200", model: "D00F", inClusters:"0x5E,0x55,0x98,0x9F,0x6C", deviceJoinName: "Vesternet VES-ZW-SWI-002 2-Wire Capable Switch"
	}
    preferences {		
		input name: "powerFailState", type: "enum", title: "Load State After Power Failure", options: [0: "off", 1: "on", 2: "previous state"], defaultValue: 2
		input name: "switchType", type: "enum", title: "Switch Type Attached", options: [0: "momentary", 1: "toggle"], defaultValue: 0		
		input name: "logEnable", type: "bool", title: "Debug"
	}
}

def getCommandClassVersions() {
	[ 0x20: 1, 0x70: 1, 0x71: 3 ]
}

def installed() {
	device.updateSetting("logEnable", [value: "true", type: "bool"])
    logDebug("installed called")
	device.updateSetting("powerFailState", [value: "2", type: "enum"])
    device.updateSetting("switchType", [value: "0", type: "enum"])
	runIn(1800,logsOff)
}

def updated() {
    logDebug("updated called")
	log.warn("debug logging is: ${settings.logEnable != false}")
	log.warn("power fail state is: ${settings.powerFailState == "0" ? "off" : settings.powerFailState == "1" ? "on" : "previous state"}")
	log.warn("switch type is: ${settings.switchType == "1" ? "toggle" : "momentary"}")
	state.clear()
	unschedule()
	if (logEnable) runIn(1800,logsOff)
	sendHubCommand(configure())
}

def configure() {
	logDebug("configure called")
	def switchType = settings.switchType ?: "0"
	def powerFailState = settings.powerFailState ?: "2"
	def cmds = delayBetween(
		[zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 2)), zwaveSecureEncap(zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: powerFailState.toInteger())), zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 2)), zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 5)), zwaveSecureEncap(zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: switchType.toInteger())), zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 5))], 1000)
	logDebug("sending ${cmds}")
	return cmds            
}

def refresh() {
	logDebug("refresh called")
	def cmd = zwaveSecureEncap(zwave.basicV1.basicGet())
	logDebug("sending ${cmd}")
	return cmd
}

def parse(String description) {
    logDebug("parse called")
    logDebug("got description: ${description}")
	def cmd = zwave.parse(description, commandClassVersions)
	if (cmd) {
		zwaveEvent(cmd)
    }
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    logDebug("physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation called")
	logDebug("got cmd: ${cmd}")
	physicalgraph.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	} 
    else {
		log.warn("Unable to extract encapsulated cmd from ${cmd}")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	logDebug("zwaveEvent physicalgraph.zwave.commands.basicv1.BasicReport called")
	logDebug("got cmd: ${cmd}")
	def switchValue = (cmd.value == 0 ? "off" : "on")
	def type = "physical"
	def descriptionText = "${device.displayName} was turned ${switchValue}"
	if (device.currentValue("switch") && switchValue == device.currentValue("switch")) {
		descriptionText = "${device.displayName} is ${switchValue}"
	}                
	if (device.currentValue("action") == "digitalon" || device.currentValue("action") == "digitaloff") {
		logDebug("action is ${device.currentValue("action")}")
		type = "digital"
		sendEvent(getEvent([name: "action", value: "standby", isStateChange: true, displayed: false]))
	}
	sendEvent(getEvent([name: "switch", value: switchValue, type: type, descriptionText: descriptionText])) 
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
	logDebug("zwaveEvent physicalgraph.zwave.commands.notificationv3.NotificationReport called")
	logDebug("got cmd: ${cmd}")
    if (cmd.notificationType == 9) {
        logDebug("got system notification event: ${cmd.event}")
        switch (cmd.event) {
            case 7:
                // emergency shutoff 
                log.warn("temperature exceeds device limit, emergency shutoff triggered!")
                break
            default:
				log.warn("skipped cmd: ${cmd}")
        }
	}
    else {
        log.warn("skipped cmd: ${cmd}")
    }
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    logDebug("physicalgraph.zwave.Command called")
	log.warn("skipped cmd: ${cmd}")
}

def on() {
	logDebug("on called")
	def cmd = zwaveSecureEncap(zwave.basicV1.basicSet(value: 0xFF))
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitalon", isStateChange: true, displayed: false]))
	return cmd
}

def off() {
	logDebug("off called")
	def cmd = zwaveSecureEncap(zwave.basicV1.basicSet(value: 0x00))
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitaloff", isStateChange: true, displayed: false])) 
	return cmd
}

def getEvent(event) {
    logDebug("getEvent called data: ${event}")
    return createEvent(event)
}

def logDebug(msg) {
	if (settings.logEnable != false) {
		log.debug("${msg}")
	}
}

def logsOff() {
    log.warn("debug logging disabled")
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def zwaveSecureEncap(cmd) {
    logDebug("zwaveSecureEncap called")
	logDebug("got cmd: ${cmd}")
	if(zwaveInfo.zw.contains("s")) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} 
    else {
		cmd.format()
	}
}