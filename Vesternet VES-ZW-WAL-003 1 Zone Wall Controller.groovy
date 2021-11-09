/**
 *	Vesternet VES-ZW-WAL-003 1 Zone Wall Controller
 * 
 */
import groovy.json.JsonOutput
metadata {
	definition (name: "Vesternet VES-ZW-WAL-003 1 Zone Wall Controller", namespace: "Vesternet", author: "Vesternet", mcdSync: true, ocfDeviceType: "x.com.st.d.remotecontroller", mnmn: "Sunricher", vid: "generic-2-button") {
		capability "Button"
		capability "Sensor"
		capability "Battery"

		fingerprint mfr: "0330", prod: "0300", model: "A307", deviceJoinName: "Vesternet VES-ZW-WAL-003 1 Zone Wall Controller"
	}
    preferences {		
		input name: "logEnable", type: "bool", title: "Debug"
	}
}

def getCommandClassVersions() {
	[ 0x5B: 1, 0x70: 1, 0x80: 1, 0x84: 2 ]
}

def installed() {
    device.updateSetting("logEnable", [value: "true", type: "bool"])
	logDebug("installed called")
	def numberOfButtons = modelNumberOfButtons[zwaveInfo.model]
    sendEvent(getEvent(name: "numberOfButtons", value: numberOfButtons, displayed: false))
    sendEvent(getEvent(name: "supportedButtonValues", value: supportedButtonValues.encodeAsJSON(), displayed: false))
    for(def buttonNumber : 1..numberOfButtons) {
        sendEvent(getEvent(name: "button", value: "pushed", data: [buttonNumber: buttonNumber], isStateChange: true, displayed: false))
    }
	setupChildDevices()
	runIn(1800,logsOff)
}

def updated() {
    logDebug("updated called")
	log.warn("debug logging is: ${logEnable != false}")
	state.clear()
	unschedule()
	if (logEnable) runIn(1800,logsOff)
}

def refreshDevice() {
	logDebug("refreshDevice called")	
	def cmds = delayBetween([zwaveSecureEncap(zwave.batteryV1.batteryGet()), zwaveSecureEncap(zwave.wakeUpV2.wakeUpNoMoreInformation())], 2000)
   	logDebug("sending ${cmds}")
	sendHubCommand(cmds)	
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

def zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    logDebug("physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification called")
    def value = eventsMap[(int) cmd.keyAttributes]
    sendEvent(getEvent(name: "button", value: value, data: [buttonNumber: cmd.sceneNumber], descriptionText: "Button Number ${cmd.sceneNumber} was ${value}", isStateChange: true))
	sendEventToChild(cmd.sceneNumber, value)
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    logDebug("physicalgraph.zwave.commands.wakeupv2.WakeUpNotification called")
	logDebug("got cmd: ${cmd}")
    runIn(1,refreshDevice)
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
    logDebug("physicalgraph.zwave.commands.batteryv1.BatteryReport called")
	logDebug("got cmd: ${cmd}")
	def batteryLevel = cmd.batteryLevel
	def descriptionText = "${device.displayName} battery level is ${batteryLevel}%"
	if (cmd.batteryLevel == 0xFF) {
		batteryLevel = 1
		descriptionText = "${device.displayName} battery level is low!"
	} 	
	sendEvent(getEvent(name: "battery", value: batteryLevel, unit: "%", descriptionText: descriptionText))
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    logDebug("physicalgraph.zwave.Command called")
	log.warn("skipped cmd: ${cmd}")
}

def setupChildDevices(){
    logDebug("setupChildDevices called")
	if(!childDevices) {
		addChildButtons(modelNumberOfButtons[zwaveInfo.model])
	}
}

def addChildButtons(numberOfButtons) {
    logDebug("addChildButtons called buttons: ${numberOfButtons}")
	for (def endpoint : 1..numberOfButtons) {
		try {
			String childDni = "${device.deviceNetworkId}:$endpoint"
			def componentLabel = (device.displayName.endsWith(' 1') ? device.displayName[0..-2] : (device.displayName + " Button ")) + "${endpoint}"
			def child = addChildDevice("Vesternet", "Vesternet VES-ZW-WAL-003 1 Zone Wall Controller Child Button", childDni, device.getHub().getId(), [
					completedSetup: true,
					label         : componentLabel,
					isComponent   : true,
					componentName : "button$endpoint",
					componentLabel: "Button $endpoint"
			])
		} 
        catch(Exception e) {
			logDebug "Exception: ${e}"
		}
	}
}

def sendEventToChild(buttonNumber, value) {
    logDebug("sendEventToChild called buttonNumber: ${buttonNumber} value: ${value}")
    String childDni = "${device.deviceNetworkId}:$buttonNumber"
	def child = childDevices.find { it.deviceNetworkId == childDni }
    child?.sendButtonEvent(buttonNumber, value)
}

def getEventsMap() {
    logDebug("getEventsMap called")
    [0: "pushed", 1: "double", 2: "held"]
}

def getModelNumberOfButtons() {
    logDebug("getModelNumberOfButtons called")
    ["A307" : 2]
}

def getSupportedButtonValues() {
    logDebug("getSupportedButtonValues called")
	def values = ["pushed", "held", "double"]	//no released value supported by smartthings 
	return values
}

def getEvent(event) {
    logDebug("getEvent called data: ${event}")
    return createEvent(event)
}

def logDebug(msg) {
	if (logEnable != false) {
		log.debug("${msg}")
	}
}

def getLogging() {
	return logEnable != false
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