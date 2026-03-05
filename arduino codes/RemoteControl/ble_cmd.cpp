#include "ble_cmd.h"
#include "buttons.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
    Serial.println("Device connected!");
  }
  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    Serial.println("Restarting advertising...");
    delay(500);
    BLEDevice::startAdvertising();
    Serial.println("Advertising restarted!");
  }
};

void ble_init() {
  Serial.begin(115200);
  Serial.println("Starting BLE...");
  
  BLEDevice::init("RemoteAssistBridge");
  BLEDevice::setPower(ESP_PWR_LVL_P9);

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();

  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMaxPreferred(0x12);  // ← was setMinPreferred twice before, now fixed
  BLEDevice::startAdvertising();
  
  Serial.println("BLE advertising started!");
}

void ble_send(const char* cmd) {
  static unsigned long last_send = 0;
  unsigned long now = millis();
  if (now - last_send < 300) return;
  last_send = now;
  
  if (deviceConnected) {
    Serial.print("Sending: ");
    Serial.println(cmd);
    pCharacteristic->setValue(cmd);
    pCharacteristic->notify();
  }
}
void check_stop() {
  if (stop_triggered) {
    stop_triggered = false;
    ble_send("CMD:STOP");
  }
}