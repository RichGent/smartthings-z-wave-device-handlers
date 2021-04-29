/**
 *	Vesternet VES-ZW-DIM-001 2-Wire Capable Dimmer
 * 
 */
import groovy.json.JsonOutput
metadata {	
	definition (name: "Vesternet VES-ZW-DIM-001 2-Wire Capable Dimmer", namespace: "Vesternet", author: "Vesternet", mcdSync: true, ocfDeviceType: "oic.d.light", mnmn: "Sunricher", vid: "39c9d95b-1181-3e94-a915-2f14024f76fc") {
		capability "Switch"
		capability "Switch Level"	
		capability "Actuator"
		capability "Sensor"
		capability "Power Meter"
		capability "Energy Meter"
		capability "Voltage Measurement"
		capability "Refresh"
		capability "Configuration"
							

		fingerprint mfr: "0330", prod: "0200", model: "D00C", inClusters:"0x5E,0x55,0x98,0x9F,0x6C", deviceJoinName: "Vesternet VES-ZW-DIM-001 2-Wire Capable Dimmer"
	}
    preferences {		
		input name: "powerFailState", type: "enum", title: "Load State After Power Failure", options: [0: "off", 1: "on", 2: "previous state"], defaultValue: 2
		input name: "switchType", type: "enum", title: "Switch Type Attached", options: [0: "momentary", 1: "toggle"], defaultValue: 0				
		input name: "logEnable", type: "bool", title: "Debug"
	}
}

def getCommandClassVersions() {
	[ 0x20: 1, 0x26: 3, 0x32: 3, 0x70: 1, 0x71: 3 ]
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
	def cmds = delayBetween([
		zwaveSecureEncap(zwave.basicV1.basicGet()),
		zwaveSecureEncap(zwave.meterV3.meterGet(scale: 0)),
		zwaveSecureEncap(zwave.meterV3.meterGet(scale: 2)),
		zwaveSecureEncap(zwave.meterV3.meterGet(scale: 4)),
		zwaveSecureEncap(zwave.meterV3.meterGet(scale: 5)),
	], 200)
	logDebug("sending ${cmds}")
	return cmds
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
	logDebug("got: ${cmd}")
	def switchValue = (cmd.value == 0 ? "off" : "on")
	def type = "physical"
	def descriptionText = "${device.displayName} was turned ${switchValue}"
	if (device.currentValue("switch") && switchValue == device.currentValue("switch")) {
		descriptionText = "${device.displayName} is ${switchValue}"
	}                
	if (device.currentValue("action") == "digitalon" || device.currentValue("action") == "digitaloff" || device.currentValue("action") == "digitalsetlevel") {
		logDebug("action is ${device.currentValue("action")}")
		type = "digital"
		sendEvent(getEvent([name: "action", value: "standby", isStateChange: true, displayed: false]))
	}
	sendEvent(getEvent([name: "switch", value: switchValue, type: type, descriptionText: descriptionText])) 
	def levelValue = cmd.value == 99 ? 100 : cmd.value
	descriptionText = "${device.displayName} was set to ${levelValue}%"
	if (device.currentValue("level") && levelValue == device.currentValue("level")) {
		descriptionText = "${device.displayName} is ${levelValue}%"
	}      
	sendEvent(getEvent(name: "level", value: levelValue, unit: "%", type: type, descriptionText: descriptionText))
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
	logDebug("zwaveEvent physicalgraph.zwave.commands.meterv3.MeterReport called")
	logDebug("got: ${cmd}")
	if (cmd.meterType == 1) {
		if (cmd.scale == 0) {
			logDebug("energy report is ${cmd.scaledMeterValue} kWh")
			sendEvent(getEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh", descriptionText: "${device.displayName} is set to ${cmd.scaledMeterValue} kWh"))
		} 
		else if (cmd.scale == 2) {
			logDebug("power report is ${cmd.scaledMeterValue} W")
			sendEvent(getEvent(name: "power", value: cmd.scaledMeterValue, unit: "W", descriptionText: "${device.displayName} is set to ${cmd.scaledMeterValue} W"))
		}
		else if (cmd.scale == 4) {
			logDebug("voltage report is ${cmd.scaledMeterValue} V")
			sendEvent(getEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V", descriptionText: "${device.displayName} is set to ${cmd.scaledMeterValue} kWh"))
		}
		else if (cmd.scale == 5) {
			logDebug("current report is ${cmd.scaledMeterValue} A")
			sendEvent(getEvent(name: "current", value: cmd.scaledMeterValue, unit: "A", descriptionText: "${device.displayName} is set to ${cmd.scaledMeterValue} kWh"))
		}
		else {
			log.warn("skipped cmd: ${cmd}")
		}
	}
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
	else if (cmd.notificationType == 8) {
        logDebug("got power management notification event: ${cmd.event}")
        switch (cmd.event) {
            case 5:
				//voltage drop / drift
                log.warn("voltage drop / drift detected!")
                break
			case 6:
				//over-current
                log.warn("over-current detected!")
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

def setLevel(level, duration = 1) {
	logDebug("setLevel called")
	if(level > 99) level = 99
	if(level < 0) level = 99
	def cmd = zwaveSecureEncap(zwave.switchMultilevelV3.switchMultilevelSet(value: level, dimmingDuration: duration))
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitalsetlevel", isStateChange: true, displayed: false]))
	return cmd
}

def startLevelChange(direction, duration = 30) {
	logDebug("startLevelChange called")
	def upDown = direction == "down"	
	def cmd = zwaveSecureEncap(zwave.switchMultilevelV3.switchMultilevelStartLevelChange(ignoreStartLevel: true, incDec:3, startLevel:0, stepSize:0, dimmingDuration: duration, upDown: upDown))
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitalsetlevel", isStateChange: true, displayed: false]))
	return cmd
}

def stopLevelChange() {
	logDebug("stopLevelChange called")
	def cmd = zwaveSecureEncap(zwave.switchMultilevelV3.switchMultilevelStopLevelChange())
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitalsetlevel", isStateChange: true, displayed: false]))
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