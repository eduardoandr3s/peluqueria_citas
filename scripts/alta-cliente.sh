#!/usr/bin/env bash
#
# Alta de una peluqueria nueva sobre una instalacion ya desplegada.
#
# Deja lista una instalacion recien levantada: crea la primera cuenta de administrador,
# rellena los datos del negocio (nombre, contacto y horario) y enciende el juego de
# modulos con el que arranca. Opcionalmente siembra tres servicios de ejemplo.
#
#   ./scripts/alta-cliente.sh /tmp/peluqueria-tal.env
#
# La configuracion va en un fichero aparte (ver scripts/alta-cliente.env.ejemplo), que NO
# se guarda en el repo: lleva la contrasena del administrador y la conexion a la base.
#
# ANTES de correrlo tiene que estar hecho lo que el script NO hace, porque no es suyo:
# crear la base, desplegar el backend con sus variables y dejar que Flyway pase las
# migraciones. El orden completo esta en docs/alta-cliente.md.
#
# Se puede volver a correr sobre una instalacion ya dada de alta: la cuenta ya existente
# no se duplica (se detecta y se sigue), y los datos del negocio y los modulos se
# reescriben con lo que diga el fichero. Lo unico que no es idempotente son los servicios
# de ejemplo, que se crearian otra vez: para eso esta SERVICIOS_DE_EJEMPLO.
set -euo pipefail

CONFIG="${1:-}"
if [[ -z "$CONFIG" || ! -f "$CONFIG" ]]; then
    echo "Uso: $0 <fichero.env>" >&2
    echo "Plantilla: scripts/alta-cliente.env.ejemplo" >&2
    exit 1
fi

for herramienta in curl jq psql; do
    command -v "$herramienta" >/dev/null || { echo "Falta $herramienta." >&2; exit 1; }
done

# shellcheck disable=SC1090
source "$CONFIG"

: "${API:?Falta API}"
: "${DB:?Falta DB}"
: "${ADMIN_EMAIL:?Falta ADMIN_EMAIL}"
: "${ADMIN_PASSWORD:?Falta ADMIN_PASSWORD}"
: "${NEGOCIO_NOMBRE:?Falta NEGOCIO_NOMBRE}"
: "${PERFIL:?Falta PERFIL}"

API="${API%/}"

paso() { echo; echo "==> $*"; }

# ---------------------------------------------------------------------------------------
# 0. Que la instalacion este viva
#
# Se comprueba antes de nada porque el caso normal es que no lo este todavia: en el plan
# gratuito de Render el primer arranque tarda, y sin esto el script fallaria mas adelante
# con un error que no dice lo que pasa.
# ---------------------------------------------------------------------------------------
paso "Esperando a que responda $API"
for intento in $(seq 1 60); do
    if curl -fsS --max-time 10 "$API/actuator/health" >/dev/null 2>&1; then
        echo "    vivo (intento $intento)"
        break
    fi
    [[ $intento -eq 60 ]] && { echo "    no responde despues de 10 minutos." >&2; exit 1; }
    sleep 10
done

# Que las migraciones hayan pasado se comprueba preguntando por la fila del negocio, que
# siembra la V17: si no esta, la base no es de esta version y todo lo demas fallaria raro.
if ! curl -fsS "$API/api/negocio" >/dev/null 2>&1; then
    echo "    /api/negocio no responde: la base no tiene la migracion V17." >&2
    exit 1
fi

# ---------------------------------------------------------------------------------------
# 1. La primera cuenta
#
# Se crea por el endpoint de registro y no con un INSERT: asi la contrasena la cifra la
# propia aplicacion y aqui no hay que fabricar ningun hash de BCrypt (el alta del equipo
# de la primera peluqueria lo hizo asi y las cuentas nacieron sin contrasena usable).
# ---------------------------------------------------------------------------------------
paso "Creando la cuenta de $ADMIN_EMAIL"
registro=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/api/auth/registro" \
    -H 'Content-Type: application/json' \
    -d "$(jq -n \
        --arg nombre "${ADMIN_NOMBRE:-Administrador}" \
        --arg email "$ADMIN_EMAIL" \
        --arg password "$ADMIN_PASSWORD" \
        --arg telefono "${ADMIN_TELEFONO:-600000000}" \
        '{nombre: $nombre, email: $email, password: $password, telefono: $telefono}')")

case "$registro" in
    2*) echo "    creada." ;;
    409|400) echo "    ya existia: se sigue con la que hay." ;;
    *)  echo "    el registro respondio $registro." >&2; exit 1 ;;
esac

# ---------------------------------------------------------------------------------------
# 2. Ascenderla a ADMIN
#
# Es lo unico que se hace por SQL, y es a proposito: un endpoint que convierta en
# administrador a quien lo pida seria una puerta abierta, y uno que solo funcione "la
# primera vez" es un estado mas que mantener. Un UPDATE contra la base, una sola vez.
# ---------------------------------------------------------------------------------------
paso "Ascendiendo la cuenta a ADMIN"
psql "$DB" -v ON_ERROR_STOP=1 -q \
    -c "UPDATE usuarios SET rol = 'ADMIN' WHERE email = '${ADMIN_EMAIL//\'/\'\'}'"
echo "    hecha administradora."

# ---------------------------------------------------------------------------------------
# 3. A partir de aqui, todo por la API con su token
# ---------------------------------------------------------------------------------------
paso "Iniciando sesion"
token=$(curl -fsS -X POST "$API/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "$(jq -n --arg email "$ADMIN_EMAIL" --arg password "$ADMIN_PASSWORD" \
        '{email: $email, password: $password}')" | jq -r '.token')
[[ -n "$token" && "$token" != "null" ]] || { echo "    no se pudo iniciar sesion." >&2; exit 1; }
echo "    sesion iniciada."

paso "Guardando los datos del negocio"
dias=$(jq -Rc 'split(",") | map(select(length > 0) | ascii_upcase | ltrimstr(" ") | rtrimstr(" "))' \
    <<<"${NEGOCIO_DIAS_CERRADOS:-}")
curl -fsS -o /dev/null -X PUT "$API/api/negocio" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
    -d "$(jq -n \
        --arg nombre "$NEGOCIO_NOMBRE" \
        --arg eslogan "${NEGOCIO_ESLOGAN:-}" \
        --arg telefono "${NEGOCIO_TELEFONO:-}" \
        --arg email "${NEGOCIO_EMAIL:-}" \
        --arg direccion "${NEGOCIO_DIRECCION:-}" \
        --arg localidad "${NEGOCIO_LOCALIDAD:-}" \
        --arg logoUrl "${NEGOCIO_LOGO_URL:-}" \
        --arg colorPrimario "${NEGOCIO_COLOR:-}" \
        --arg horaApertura "${NEGOCIO_APERTURA:-09:00}" \
        --arg horaCierre "${NEGOCIO_CIERRE:-20:00}" \
        --argjson diasCerrados "$dias" \
        '{nombre: $nombre, eslogan: $eslogan, telefono: $telefono, email: $email,
          direccion: $direccion, localidad: $localidad, logoUrl: $logoUrl,
          colorPrimario: $colorPrimario, horaApertura: $horaApertura,
          horaCierre: $horaCierre, diasCerrados: $diasCerrados}')"
echo "    $NEGOCIO_NOMBRE, de ${NEGOCIO_APERTURA:-09:00} a ${NEGOCIO_CIERRE:-20:00}."

paso "Aplicando el perfil $PERFIL"
curl -fsS -o /dev/null -X POST "$API/api/modulos/perfiles/$PERFIL" \
    -H "Authorization: Bearer $token"
encendidos=$(curl -fsS "$API/api/modulos/activos" | jq -r '.modulos | join(", ")')
echo "    encendidos: $encendidos"

if [[ "${SERVICIOS_DE_EJEMPLO:-false}" == "true" ]]; then
    paso "Sembrando tres servicios de ejemplo"
    # Para que el catalogo no nazca vacio y se pueda probar a agendar el mismo dia. Se
    # editan o se borran despues desde el panel.
    while IFS='|' read -r nombre precio duracion; do
        curl -fsS -o /dev/null -X POST "$API/api/servicios" \
            -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
            -d "$(jq -n --arg n "$nombre" --argjson p "$precio" --argjson d "$duracion" \
                '{nombre: $n, descripcion: "Servicio de ejemplo: editalo o borralo.",
                  precio: $p, duracion: $d}')"
        echo "    $nombre ($precio EUR, $duracion min)"
    done <<'SERVICIOS'
Corte de pelo|15|30
Corte y barba|22|45
Tinte|35|90
SERVICIOS
fi

cat <<FIN

==> Alta terminada.

    Panel:   entra con $ADMIN_EMAIL
    Modulos: $encendidos

    Lo que queda por hacer a mano, y no lo hace este script:
      - Dar de alta al equipo (Peluqueros) y su CV, desde el panel.
      - Repasar los permisos por rol: los de galeria y el cobro manual nacen apagados.
      - Si esta peluqueria va a cobrar con tarjeta: poner las claves de Stripe de SU
        cuenta en el servidor y encender despues el modulo «Pago con tarjeta».
FIN
