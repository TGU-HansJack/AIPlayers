import path from 'path';

function resolveDataRoot() {
    const configured = process.env.AIPLAYERS_BOTS_ROOT;
    if (configured && configured.trim().length > 0) {
        return path.resolve(configured.trim());
    }
    return path.resolve('./bots');
}

export function getRuntimeRoot() {
    return path.resolve('.');
}

export function getRuntimeBotsRoot() {
    return path.join(getRuntimeRoot(), 'bots');
}

export function getRuntimeBotAssetPath(...segments) {
    return path.join(getRuntimeBotsRoot(), ...segments);
}

export function getBotsRoot() {
    return resolveDataRoot();
}

export function getBotRoot(name) {
    return path.join(resolveDataRoot(), name);
}

export function getBotSubPath(name, ...segments) {
    return path.join(getBotRoot(name), ...segments);
}