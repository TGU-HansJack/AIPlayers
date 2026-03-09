import settings from '../settings.js';

let viewerDisabledByRuntime = false;

export async function addBrowserViewer(bot, count_id) {
    if (!settings.render_bot_view || viewerDisabledByRuntime) {
        return;
    }
    try {
        const prismarineViewer = await import('prismarine-viewer');
        const mineflayerViewer = prismarineViewer.default.mineflayer;
        mineflayerViewer(bot, { port: 3000 + count_id, firstPerson: true });
    } catch (error) {
        viewerDisabledByRuntime = true;
        settings.render_bot_view = false;
        console.warn('Browser viewer disabled:', error?.message || error);
    }
}