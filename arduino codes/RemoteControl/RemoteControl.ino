#include "buttons.h"
#include "ble_cmd.h"

static bool connect_last = HIGH;
static bool confirm_last = HIGH;
static bool stop_last = HIGH;

void setup() {
  buttons_init();
  ble_init();
  attachInterrupt(digitalPinToInterrupt(BTN_STOP), stop_ISR, FALLING);
}

void loop() {
  bool connect_now = digitalRead(BTN_CONNECT);
  bool confirm_now = digitalRead(BTN_CONFIRM);
  bool stop_now = digitalRead(BTN_STOP);

  if (connect_now == LOW && connect_last == HIGH) {
    Serial.println("CONNECT pressed!");
    ble_send("CMD:CONNECT");
  }
  if (confirm_now == LOW && confirm_last == HIGH) {
    Serial.println("CONFIRM pressed!");
    ble_send("CMD:CONFIRM");
  }
  if (stop_now == LOW && stop_last == HIGH) {
    Serial.println("STOP pressed!");
    ble_send("CMD:STOP");
  }

  connect_last = connect_now;
  confirm_last = confirm_now;
  stop_last = stop_now;

  check_stop();
}

