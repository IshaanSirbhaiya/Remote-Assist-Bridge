#ifndef BUTTONS_H
#define BUTTONS_H

#include <Arduino.h>   // REQUIRED for IRAM_ATTR on ESP32

#define BTN_CONNECT  20
#define BTN_CONFIRM  21
#define BTN_STOP     10
#define DEBOUNCE_MS  50

extern volatile bool stop_triggered;

void buttons_init();
bool button_pressed(int pin);
void IRAM_ATTR stop_ISR();

#endif