import path from 'path';

function resolveBaseDir() {
    const configured = process.env.AIPLAYERS_BOTS_ROOT;
    if (configured && configured.trim().length > 0) {
        return path.resolve(configured.trim());
    }
    return path.resolve('./bots');
}

export function getBotsRoot() {
    return resolveBaseDir();
}

export function getBotRoot(name) {
    return path.join(resolveBaseDir(), name);
}

export function getBotSubPath(name, ...segments) {
    return path.join(getBotRoot(name), ...segments);
}
