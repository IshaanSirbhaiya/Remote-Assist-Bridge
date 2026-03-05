#include "buttons.h"
#include <Arduino.h>

static unsigned long last_press[3] = {0, 0, 0};
static bool last_state[3] = {HIGH, HIGH, HIGH};
volatile bool stop_triggered = false;

void buttons_init() {
  pinMode(BTN_CONNECT, INPUT_PULLUP);
  pinMode(BTN_CONFIRM, INPUT_PULLUP);
  pinMode(BTN_STOP,    INPUT_PULLUP);
}

int pinIndex(int pin) {
  if (pin == BTN_CONNECT) return 0;
  if (pin == BTN_CONFIRM) return 1;
  return 2;
}

bool button_pressed(int pin) {
  int i = pinIndex(pin);
  bool current = digitalRead(pin);
  if (current == LOW && last_state[i] == HIGH) {
    unsigned long now = millis();
    if (now - last_press[i] > DEBOUNCE_MS) {
      last_press[i] = now;
      last_state[i] = LOW;
      return true;
    }
  }
  if (current == HIGH) last_state[i] = HIGH;
  return false;
}

void IRAM_ATTR stop_ISR() {
  stop_triggered = true;
}