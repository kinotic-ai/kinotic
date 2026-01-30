import { ConnectionInfo } from "@kinotic/continuum-client";

export const getQueryParam = (name: string) => {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get(name);
};

export const getBaseUrl = () => {
    let prefix = 'http';
    let port = '5173';
    if (window.location.protocol.startsWith('https')) {
        prefix = 'https';
        port = '443';
    }
    return `${prefix}://${window.location.hostname}:${port}`;
};



export function createConnectionInfo(): ConnectionInfo {
    // Use build time variable if available, otherwise use default
    const envPort = import.meta.env.VITE_CONTINUUM_PORT ? parseInt(import.meta.env.VITE_CONTINUUM_PORT) : 58503
    const envHost = import.meta.env.VITE_CONTINUUM_HOST ? import.meta.env.VITE_CONTINUUM_HOST : 'localhost'
    const envUseSSL = import.meta.env.VITE_CONTINUUM_USE_SSL ? import.meta.env.VITE_CONTINUUM_USE_SSL === 'true' : false

    const connectionInfo: ConnectionInfo = {
        host: envHost,
        port: envPort 
    }

    if (envUseSSL || window.location.protocol.startsWith('https')) {
        connectionInfo.useSSL = true
    }

    // Auto-detect from window location if not localhost
    if (window.location.hostname !== '127.0.0.1'
        && window.location.hostname !== 'localhost') {

        connectionInfo.host = window.location.hostname

    }

    // we are using ssl and no port is in use so we assume a proxy
    // is in use and we default to 443
    if (connectionInfo.useSSL
        && window.location.port === '') {
        connectionInfo.port = 443
    }
    return connectionInfo
}