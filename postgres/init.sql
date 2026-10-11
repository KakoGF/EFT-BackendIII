-- Se ejecuta una sola vez, cuando el volumen de PostgreSQL se crea por primera vez.
-- Cada microservicio tiene su propia base de datos (database per service).
CREATE DATABASE bancoxyz_cuentas;
CREATE DATABASE bancoxyz_pagos;
CREATE DATABASE bancoxyz_clientes;
CREATE DATABASE bancoxyz_batch;
