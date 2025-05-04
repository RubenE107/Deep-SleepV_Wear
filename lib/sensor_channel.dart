import 'package:flutter/services.dart';

class SensorChannel {
  static const MethodChannel _channel =
      MethodChannel('com.example.watch/sensors');

  static Future<int> getHeartRate() async {
  try {
    return await _channel.invokeMethod('getHeartRate');
  } catch (_) {
    return -1;
  }
}


  static Future<Map<String, double>> getAccelerometer() async {
    final Map<dynamic, dynamic> result =
        await _channel.invokeMethod('getAccelerometer');
    return {
      "x": result["x"],
      "y": result["y"],
      "z": result["z"],
    };
  }

  static Future<int> getSteps() async {
    final int result = await _channel.invokeMethod('getSteps');
    return result;
  }
}