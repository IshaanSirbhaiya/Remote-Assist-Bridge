#ifndef BLE_CMD_H
#define BLE_CMD_H

void ble_init();
void ble_send(const char* cmd);
void check_stop();

#endif