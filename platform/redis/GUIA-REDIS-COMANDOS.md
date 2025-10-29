# Guía rápida: Comandos básicos para gestionar Redis en Docker

## 1. Acceder al contenedor de Redis

```bash
# Verifica el nombre del contenedor (ejemplo: platform_redis_1)
docker ps | grep redis

# Accede al contenedor y abre la CLI de Redis
docker exec -it platform_redis_1 redis-cli
```

---

## 2. Comandos básicos en redis-cli

### Listar todas las claves
```bash
KEYS *
```

### Listar solo claves de alertas activas (según tu proyecto)
```bash
KEYS alert:active:*
```

### Ver el contenido de una clave específica
```bash
GET alert:active:Norte
GET alert:active:Centro
```

### Ver TTL (tiempo de vida) de una clave
```bash
TTL alert:active:Norte
```

### Eliminar una clave
```bash
DEL alert:active:Norte
```

---

## 3. Consultas útiles para tu proyecto

### Ver todas las alertas activas en Redis
```bash
KEYS alert:active:*
```

### Ver el contenido de todas las alertas activas
```bash
for key in $(redis-cli KEYS 'alert:active:*'); do echo $key; redis-cli GET $key; done
```

### Buscar claves por zona
```bash
KEYS alert:active:Zona*
```

---

## 4. Comandos avanzados

### Ver estadísticas generales
```bash
INFO
```

### Ver memoria usada
```bash
MEMORY STATS
```

### Eliminar todas las claves (¡Cuidado!)
```bash
FLUSHALL
```

---

## 5. Salir de redis-cli
```bash
exit
```

---

## 6. Buenas prácticas para tu proyecto
- Usa prefijos claros en las claves (`alert:active:<zona>`) para evitar colisiones.
- Verifica el TTL para asegurar que las alertas activas expiren correctamente.
- Elimina claves obsoletas para mantener Redis limpio.
- No uses `FLUSHALL` en producción, solo para pruebas o limpieza total.
- Si usas scripts, puedes automatizar la consulta y eliminación de claves específicas.

---

## 7. Resumen de comandos útiles
| Acción                  | Comando                        |
|------------------------|--------------------------------|
| Listar claves          | KEYS *                         |
| Ver claves de alertas  | KEYS alert:active:*            |
| Ver contenido de clave | GET <clave>                    |
| Ver TTL                | TTL <clave>                    |
| Eliminar clave         | DEL <clave>                    |
| Estadísticas           | INFO                           |
| Salir                  | exit                           |
