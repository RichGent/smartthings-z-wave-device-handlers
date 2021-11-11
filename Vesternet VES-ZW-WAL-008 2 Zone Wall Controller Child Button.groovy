/**
 *	Vesternet VES-ZW-WAL-008 2 Zone Wall Controller Child Button
 * 
 */
import groovy.json.JsonOutput
metadata {
	definition (name: "Vesternet VES-ZW-WAL-008 2 Zone Wall Controller Child Button", namespace: "Vesternet", author: "Vesternet", ocfDeviceType: "x.com.st.d.remotecontroller") {
		capability "Button"
		capability "Sensor"
	}
}

def installed() {
    logDebug("installed called")
	sendEvent(getEvent(name: "numberOfButtons", value: "1", displayed: false))
    sendEvent(getEvent(name: "supportedButtonValues", value: supportedButtonValues.encodeAsJSON(), displayed: false))
    sendEvent(getEvent(name: "button", value: "pushed", isStateChange: true, displayed: false))
}

def updated() {
    logDebug("updated called")
}

def sendButtonEvent(buttonNumber, value) {
    logDebug("sendButtonEvent called buttonNumber: ${buttonNumber} value: ${value}")
    sendEvent(getEvent(name: "button", value: value, descriptionText: "Button Number ${buttonNumber} was ${value}", data: [buttonNumber: buttonNumber], isStateChange: true))    
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
	if (parent.logging) {
		log.debug("${msg}")
	}
}