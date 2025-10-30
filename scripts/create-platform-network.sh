#!/bin/sh
# Script para crear la red Docker externa 'platform_default' si no existe

NETWORK_NAME="platform_default"

if ! docker network inspect $NETWORK_NAME >/dev/null 2>&1; then
  echo "Creando red externa Docker: $NETWORK_NAME"
  docker network create $NETWORK_NAME
else
  echo "La red externa Docker '$NETWORK_NAME' ya existe."
fi
