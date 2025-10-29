# Creación de usuario administrador en Airflow

Para poder acceder a la interfaz web de Airflow, es necesario crear un usuario administrador. Una vez que el servicio de Airflow esté corriendo, ejecuta el siguiente comando en tu terminal:

```bash
docker exec -it platform-airflow-webserver airflow users create --username admin --firstname Admin --lastname User --role Admin --email admin@admin.com --password admin
```

Esto creará un usuario con las siguientes credenciales:
- **Usuario:** admin
- **Contraseña:** admin

Accede a Airflow en: http://localhost:8082


> **Importante:** Después de crear el usuario, reinicia el servicio de Airflow para que los cambios tengan efecto. Puedes hacerlo con:
```bash
docker compose restart platform-airflow-webserver
```

> **Nota:** Si eliminaste el servicio de base de datos local, Airflow debe estar configurado para conectarse a Supabase (o la base de datos remota) usando las variables de entorno en tu archivo `.env`. El servicio de Airflow sigue funcionando en Docker, pero la base de datos ya no está en tu máquina local.
