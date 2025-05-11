import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sensores del Reloj',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const SensorHomePage(),
    );
  }
}

class SensorHomePage extends StatefulWidget {
  const SensorHomePage({super.key});

  @override
  State<SensorHomePage> createState() => _SensorHomePageState();
}

class _SensorHomePageState extends State<SensorHomePage> {
  static const MethodChannel _methodChannel = MethodChannel('com.example.flutter/notify');
  static const EventChannel _eventChannel = EventChannel('com.example.watch/sensorUpdates');

  String _heartRate = "Cargando...";
  String _accelerometer = "Cargando...";
  String _steps = "Cargando...";

  @override
  void initState() {
    super.initState();
    _listenToSensorUpdates();
  }

  Future<void> _enviarNotificacion() async {
    try {
      await _methodChannel.invokeMethod("showNotification");
    } catch (e) {
      print("❌ Error al enviar notificación: $e");
    }
  }

  void _listenToSensorUpdates() {
    _eventChannel.receiveBroadcastStream().listen((event) {
      try {
        setState(() {
          _heartRate = "${event['heartRate']?.toStringAsFixed(1) ?? 'N/A'} bpm";
          final accel = event['accelerometer'];
          _accelerometer = accel != null
              ? "x: ${accel['x']?.toStringAsFixed(2)}, y: ${accel['y']?.toStringAsFixed(2)}, z: ${accel['z']?.toStringAsFixed(2)}"
              : "No disponible";
          _steps = "${event['steps']?.toInt() ?? 'N/A'} pasos";
        });
      } catch (e) {
        print("Error procesando los datos: $e");
      }
    }, onError: (error) {
      print("Error al recibir datos de los sensores: $error");
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Sensores del Reloj')),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text("Frecuencia Cardíaca: $_heartRate"),
              const SizedBox(height: 10),
              Text("Acelerómetro: $_accelerometer"),
              const SizedBox(height: 10),
              Text("Pasos: $_steps"),
            ],
          ),
        ),
      ),
      
    );
  }
}
