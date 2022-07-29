/**
 *	Vesternet VES-ZW-MOT-018 Motor Controller
 * 
 */
import groovy.json.JsonOutput
metadata {	
	definition (name: "Vesternet VES-ZW-MOT-018 Motor Controller", namespace: "Vesternet", author: "Sunricher", mcdSync: true, ocfDeviceType: "oic.d.blind", mnmn: "SmartThings", vid: "generic-shade-3") {
		capability "Window Shade"
		capability "Window Shade Level"
		capability "Switch Level"
		capability "Actuator"
		capability "Sensor"
		capability "Power Meter"
		capability "Energy Meter"
		capability "Voltage Measurement"
		capability "Refresh"		
		capability "Configuration"
        
        command "stop"
		
		fingerprint mfr: "0330", prod: "0004", model: "D00D", inClusters: "0x5E,0x55,0x98,0x9F,0x6C", deviceJoinName: "Vesternet VES-ZW-MOT-018 Motor Controller"      
	}
    preferences {		
		input name: "logEnable", type: "bool", title: "Debug"
	}
}

def getCommandClassVersions() {
	[ 0x20: 1, 0x25: 1, 0x32: 3, 0x60: 3, 0x70: 1, 0x71: 3, 0x5B: 3 ]//multilevel 0x26 v4 on device
}

def installed() {
	device.updateSetting("logEnable", [value: "true", type: "bool"])
    logDebug("installed called")
	runIn(1800,logsOff)
}

def updated() {
    logDebug("updated called")
	log.warn("debug logging is: ${settings.logEnable != false}")
	state.clear()
	unschedule()
	if (logEnable) runIn(1800,logsOff)
}

def configure() {
	logDebug("configure called")	
}

def refresh() {
	logDebug("refresh called")
	def cmd = delayBetween([zwaveSecureEncap(zwave.basicV1.basicGet()), zwaveSecureEncap(zwave.switchMultilevelV3.switchMultilevelGet()), zwaveSecureEncap(zwave.meterV5.meterGet(scale: 0)), zwaveSecureEncap(zwave.meterV5.meterGet(scale: 2)), zwaveSecureEncap(zwave.meterV5.meterGet(scale: 4)), zwaveSecureEncap(zwave.meterV5.meterGet(scale: 5))], 1000)
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
    handleLevelReport(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	logDebug("zwaveEvent physicalgraph.zwave.commands.basicv1.BasicReport called")
	logDebug("got cmd: ${cmd}")
    handleLevelReport(cmd)
}

def handleLevelReport(physicalgraph.zwave.Command cmd) {
	logDebug("handleLevelReport called")
	logDebug("got cmd: ${cmd}")
    def levelValue = cmd.value == 99 ? 100 : cmd.value
    logDebug("current position is ${levelValue}")	                        
    def descriptionText = "${device.displayName} was set to ${levelValue}%"
    def currentValue =  device.currentValue("shadeLevel") ?: "unknown"
    if (levelValue == currentValue) {
        descriptionText = "${device.displayName} is ${levelValue}%"
    }  
    def type = "physical"
    def action = device.currentValue("action") ?: "standby"
    if (action == "digitalsetlevel" || action == "digitalopen" || action == "digitalclose") {
        logDebug("action is ${action}")
        type = "digital"        
		sendEvent(getEvent([name: "action", value: "standby", isStateChange: true, displayed: false]))
        logDebug("action set to standby")
    }
    sendEvent(getEvent([name: "shadeLevel", value: levelValue, unit: "%", type: type, descriptionText: descriptionText]))
	sendEvent(getEvent([name: "level", value: levelValue, unit: "%", type: type, descriptionText: descriptionText]))
    if (levelValue == 0) {
        sendEvent(getEvent([name: "windowShade", value: "closed", type: type, descriptionText: "${device.displayName} is closed"]))
    }
    else if (levelValue == 100) {
        sendEvent(getEvent([name: "windowShade", value: "open", type: type, descriptionText: "${device.displayName} is open"]))
    }
    else {
        sendEvent(getEvent([name: "windowShade", value: "partially open", type: type, descriptionText: "${device.displayName} is partially open"]))
    }
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
			case 8:
                // overload detected
                log.warn("load exceeds device limit")
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

def open() {
	logDebug("open called")
	def cmd = zwaveSecureEncap(zwave.switchMultilevelV3.switchMultilevelSet(value: 99, dimmingDuration: 1))
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitalopen", isStateChange: true, displayed: false]))
	sendEvent(getEvent([name: "windowShade", value: "opening", type: "digital", descriptionText: "${device.displayName} is opening"]))
	return cmd
}

def close() {
	logDebug("close called")
	def cmd = zwaveSecureEncap(zwave.switchMultilevelV3.switchMultilevelSet(value: 0, dimmingDuration: 1))
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitalclose", isStateChange: true, displayed: false])) 
	sendEvent(getEvent([name: "windowShade", value: "closing", type: "digital", descriptionText: "${device.displayName} is closing"]))
	return cmd
}

def setLevel(level, duration = 1) {
	logDebug("setLevel called")
	if(level > 99) level = 99
	if(level < 0) level = 99
	def cmd = zwaveSecureEncap(zwave.switchMultilevelV3.switchMultilevelSet(value: level, dimmingDuration: duration))
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitalsetlevel", isStateChange: true, displayed: false]))
	def currentValue =  device.currentValue("shadeLevel") ?: "unknown"
    if (level < currentValue) {
        sendEvent(getEvent([name: "windowShade", value: "closing", type: "digital", descriptionText: "${device.displayName} is closing"]))
    }  
    else if (level > currentValue) {
        sendEvent(getEvent([name: "windowShade", value: "opening", type: "digital", descriptionText: "${device.displayName} is opening"]))
    }  
	return cmd
}

def startLevelChange(direction, duration = 30) {
	logDebug("startLevelChange called")
	def upDown = direction == "down"	
	def cmd = zwaveSecureEncap(zwave.switchMultilevelV3.switchMultilevelStartLevelChange(ignoreStartLevel: true, incDec:3, startLevel:0, stepSize:0, dimmingDuration: duration, upDown: upDown))
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitalsetlevel", isStateChange: true, displayed: false]))
	def currentValue =  device.currentValue("shadeLevel") ?: "unknown"
    if (level < currentValue) {
        sendEvent(getEvent([name: "windowShade", value: "closing", type: "digital", descriptionText: "${device.displayName} is closing"]))
    }  
    else if (level > currentValue) {
        sendEvent(getEvent([name: "windowShade", value: "opening", type: "digital", descriptionText: "${device.displayName} is opening"]))
    }  
	return cmd
}

def stopLevelChange() {
	logDebug("stopLevelChange called")
	def cmd = zwaveSecureEncap(zwave.switchMultilevelV3.switchMultilevelStopLevelChange())
	logDebug("sending ${cmd}")
	sendEvent(getEvent([name: "action", value: "digitalsetlevel", isStateChange: true, displayed: false]))
	sendEvent(getEvent([name: "windowShade", value: "partially open", type: "digital", descriptionText: "${device.displayName} is stopping"]))
	return cmd
}

def pause() {
	logDebug("pause called")
	stopLevelChange()
}

def stop() {
	logDebug("stop called")
	stopLevelChange()
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