import { Server } from 'socket.io';
import express from 'express';
import http from 'http';
import path from 'path';
import { fileURLToPath } from 'url';
import * as mindcraft from './mindcraft.js';
import { readFileSync } from 'fs';
import { readJsonFile } from '../utils/json.js';
import settings from '../../settings.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Mindserver is:
// - central hub for communication between all agent processes
// - api to control from other languages and remote users
// - host for webapp

let io;
let server;
const agent_connections = {};
const agent_listeners = [];

const settings_spec = readJsonFile(path.join(__dirname, 'public/settings_spec.json'));

class AgentConnection {
    constructor(agentSettings, viewerPort) {
        this.socket = null;
        this.settings = agentSettings;
        this.in_game = false;
        this.full_state = null;
        this.viewer_port = viewerPort;
        this.last_output = '';
        this.last_error = null;
        this.owner_name = agentSettings?.aiplayers_owner_name || agentSettings?.only_chat_with?.[0] || '';
        this.owner_uuid = agentSettings?.aiplayers_owner_uuid || '';
    }

    setSettings(agentSettings) {
        this.settings = agentSettings;
        this.owner_name = agentSettings?.aiplayers_owner_name || agentSettings?.only_chat_with?.[0] || this.owner_name || '';
        this.owner_uuid = agentSettings?.aiplayers_owner_uuid || this.owner_uuid || '';
    }
}

export function registerAgent(agentSettings, viewerPort) {
    agent_connections[agentSettings.profile.name] = new AgentConnection(agentSettings, viewerPort);
}

export function logoutAgent(agentName) {
    if (agent_connections[agentName]) {
        agent_connections[agentName].in_game = false;
        agentsStatusUpdate();
    }
}

export function createMindServer(host_public = false, port = 8080) {
    const app = express();
    server = http.createServer(app);
    io = new Server(server);

    app.use(express.json({ limit: '1mb' }));
    app.use(express.static(path.join(__dirname, 'public')));
    registerRestApi(app);

    io.on('connection', (socket) => {
        let curAgentName = null;
        console.log('Client connected');

        agentsStatusUpdate(socket);

        socket.on('create-agent', async (agentSettings, callback) => {
            console.log('API create agent...');
            try {
                const normalized = applySettingsDefaults(agentSettings);
                if (normalized.profile.name in agent_connections) {
                    callback({ success: false, error: 'Agent already exists' });
                    return;
                }
                const returned = await mindcraft.createAgent(normalized);
                callback({ success: returned.success, error: returned.error });
                const name = normalized.profile.name;
                if (!returned.success && agent_connections[name]) {
                    agent_connections[name].last_error = returned.error;
                    mindcraft.destroyAgent(name);
                    delete agent_connections[name];
                }
                agentsStatusUpdate();
            } catch (error) {
                callback({ success: false, error: String(error) });
            }
        });

        socket.on('get-settings', (agentName, callback) => {
            if (agent_connections[agentName]) {
                callback({ settings: agent_connections[agentName].settings });
            } else {
                callback({ error: `Agent '${agentName}' not found.` });
            }
        });

        socket.on('connect-agent-process', (agentName) => {
            if (agent_connections[agentName]) {
                agent_connections[agentName].socket = socket;
                agentsStatusUpdate();
            }
        });

        socket.on('login-agent', (agentName) => {
            if (agent_connections[agentName]) {
                agent_connections[agentName].socket = socket;
                agent_connections[agentName].in_game = true;
                curAgentName = agentName;
                agentsStatusUpdate();
            } else {
                console.warn(`Unregistered agent ${agentName} tried to login`);
            }
        });

        socket.on('disconnect', () => {
            if (agent_connections[curAgentName]) {
                console.log(`Agent ${curAgentName} disconnected`);
                agent_connections[curAgentName].in_game = false;
                agent_connections[curAgentName].socket = null;
                agentsStatusUpdate();
            }
            if (agent_listeners.includes(socket)) {
                removeListener(socket);
            }
        });

        socket.on('chat-message', (agentName, json) => {
            if (!agent_connections[agentName]) {
                console.warn(`Agent ${agentName} tried to send a message but is not logged in`);
                return;
            }
            console.log(`${curAgentName} sending message to ${agentName}: ${json.message}`);
            agent_connections[agentName].socket?.emit('chat-message', curAgentName, json);
        });

        socket.on('set-agent-settings', (agentName, agentSettings) => {
            const agent = agent_connections[agentName];
            if (agent) {
                agent.setSettings(agentSettings);
                agent.socket?.emit('restart-agent');
            }
        });

        socket.on('restart-agent', (agentName) => {
            console.log(`Restarting agent: ${agentName}`);
            agent_connections[agentName]?.socket?.emit('restart-agent');
        });

        socket.on('stop-agent', (agentName) => {
            mindcraft.stopAgent(agentName);
        });

        socket.on('start-agent', (agentName) => {
            mindcraft.startAgent(agentName);
        });

        socket.on('destroy-agent', (agentName) => {
            if (agent_connections[agentName]) {
                mindcraft.destroyAgent(agentName);
                delete agent_connections[agentName];
            }
            agentsStatusUpdate();
        });

        socket.on('stop-all-agents', () => {
            console.log('Killing all agents');
            for (let agentName in agent_connections) {
                mindcraft.stopAgent(agentName);
            }
        });

        socket.on('shutdown', () => {
            console.log('Shutting down');
            for (let agentName in agent_connections) {
                mindcraft.stopAgent(agentName);
            }
            setTimeout(() => {
                console.log('Exiting MindServer');
                process.exit(0);
            }, 2000);
        });

        socket.on('send-message', (agentName, data) => {
            if (!agent_connections[agentName]) {
                console.warn(`Agent ${agentName} not in game, cannot send message via MindServer.`);
                return;
            }
            try {
                agent_connections[agentName].socket?.emit('send-message', data);
            } catch (error) {
                agent_connections[agentName].last_error = String(error);
                console.error('Error: ', error);
            }
        });

        socket.on('bot-output', (agentName, message) => {
            if (agent_connections[agentName]) {
                agent_connections[agentName].last_output = message;
            }
            io.emit('bot-output', agentName, message);
        });

        socket.on('listen-to-agents', () => {
            addListener(socket);
        });
    });

    const host = host_public ? '0.0.0.0' : 'localhost';
    server.listen(port, host, () => {
        console.log(`MindServer running on port ${port}`);
    });

    return server;
}

function registerRestApi(app) {
    app.get('/api/aiplayers/health', (req, res) => {
        res.json({
            ok: true,
            mindserverPort: Number(settings.mindserver_port || 8080),
            botCount: Object.keys(agent_connections).length,
            agents: buildAgentList(),
        });
    });

    app.get('/api/aiplayers/bots', async (req, res) => {
        res.json({ ok: true, agents: await buildAgentListWithState() });
    });

    app.post('/api/aiplayers/bots', async (req, res) => {
        try {
            const payload = req.body || {};
            const botName = String(payload.name || '').trim();
            if (!botName) {
                res.status(400).json({ success: false, error: 'Bot name is required.' });
                return;
            }
            if (agent_connections[botName]) {
                res.status(409).json({ success: false, error: 'Agent already exists.' });
                return;
            }
            const agentSettings = buildAgentSettings(payload);
            const result = await mindcraft.createAgent(agentSettings);
            if (!result.success) {
                if (agent_connections[botName]) {
                    agent_connections[botName].last_error = result.error || 'Create failed.';
                }
                res.status(500).json({ success: false, error: result.error || 'Failed to create agent.' });
                return;
            }
            res.json({ success: true, agent: await buildAgentDetails(botName) });
        } catch (error) {
            res.status(500).json({ success: false, error: String(error) });
        }
    });

    app.get('/api/aiplayers/bots/:name', async (req, res) => {
        const agent = await buildAgentDetails(req.params.name);
        if (!agent) {
            res.status(404).json({ success: false, error: 'Agent not found.' });
            return;
        }
        res.json({ success: true, agent });
    });

    app.get('/api/aiplayers/bots/:name/state', async (req, res) => {
        const state = await getAgentState(req.params.name);
        if (state == null) {
            res.status(404).json({ success: false, error: 'Agent state not available.' });
            return;
        }
        res.json({ success: true, state });
    });

    app.post('/api/aiplayers/bots/:name/message', async (req, res) => {
        const agentName = req.params.name;
        const agent = agent_connections[agentName];
        if (!agent || !agent.socket) {
            res.status(404).json({ success: false, error: 'Agent not connected.' });
            return;
        }
        const from = String(req.body?.from || 'ADMIN').trim() || 'ADMIN';
        const message = String(req.body?.message || '').trim();
        if (!message) {
            res.status(400).json({ success: false, error: 'Message is required.' });
            return;
        }
        agent.socket.emit('send-message', { from, message });
        res.json({ success: true });
    });

    app.post('/api/aiplayers/bots/:name/start', (req, res) => {
        const agentName = req.params.name;
        if (!agent_connections[agentName]) {
            res.status(404).json({ success: false, error: 'Agent not found.' });
            return;
        }
        mindcraft.startAgent(agentName);
        res.json({ success: true });
    });

    app.post('/api/aiplayers/bots/:name/stop', (req, res) => {
        const agentName = req.params.name;
        if (!agent_connections[agentName]) {
            res.status(404).json({ success: false, error: 'Agent not found.' });
            return;
        }
        mindcraft.stopAgent(agentName);
        res.json({ success: true });
    });

    app.delete('/api/aiplayers/bots/:name', (req, res) => {
        const agentName = req.params.name;
        if (!agent_connections[agentName]) {
            res.status(404).json({ success: false, error: 'Agent not found.' });
            return;
        }
        mindcraft.destroyAgent(agentName);
        delete agent_connections[agentName];
        agentsStatusUpdate();
        res.json({ success: true });
    });

    app.post('/api/aiplayers/bots/:name/bind-owner', (req, res) => {
        const agentName = req.params.name;
        const agent = agent_connections[agentName];
        if (!agent) {
            res.status(404).json({ success: false, error: 'Agent not found.' });
            return;
        }
        const ownerName = String(req.body?.ownerName || '').trim();
        const ownerUuid = String(req.body?.ownerUuid || '').trim();
        const nextSettings = {
            ...agent.settings,
            only_chat_with: ownerName ? [ownerName] : [],
            aiplayers_owner_name: ownerName,
            aiplayers_owner_uuid: ownerUuid,
        };
        agent.setSettings(nextSettings);
        agent.socket?.emit('restart-agent');
        res.json({ success: true, agent: buildAgentConnectionState(agentName, agent) });
    });
}

function applySettingsDefaults(rawSettings) {
    const normalized = {};
    for (let key in settings_spec) {
        if (!(key in rawSettings)) {
            if (settings_spec[key].required) {
                throw new Error(`Setting ${key} is required`);
            }
            normalized[key] = settings_spec[key].default;
        } else {
            normalized[key] = rawSettings[key];
        }
    }
    if (!normalized.profile?.name) {
        throw new Error('Agent name is required in profile');
    }
    return normalized;
}

function buildAgentSettings(payload) {
    const profilePath = path.resolve(process.cwd(), String(payload.profilePath || './andy.json').trim());
    const profile = readJsonFile(profilePath);
    if (payload.profile && typeof payload.profile === 'object') {
        Object.assign(profile, payload.profile);
    }
    profile.name = String(payload.name || profile.name || '').trim();

    return applySettingsDefaults({
        profile,
        minecraft_version: payload.minecraftVersion || settings.minecraft_version || 'auto',
        host: String(payload.host || settings.host || '127.0.0.1'),
        port: Number(payload.port ?? settings.port ?? 25565),
        auth: 'offline',
        base_profile: payload.baseProfile || settings.base_profile || 'assistant',
        load_memory: Boolean(payload.loadMemory ?? true),
        init_message: payload.initMessage || settings.init_message || 'Respond with hello world and your name',
        only_chat_with: payload.ownerName ? [String(payload.ownerName)] : [],
        speak: Boolean(payload.speak ?? false),
        language: payload.language || 'zh',
        allow_vision: Boolean(payload.allowVision ?? settings.allow_vision ?? false),
        render_bot_view: Boolean(payload.renderBotView ?? settings.render_bot_view ?? false),
        blocked_actions: payload.blockedActions || settings.blocked_actions || [],
        code_timeout_mins: Number(payload.codeTimeoutMins ?? settings.code_timeout_mins ?? -1),
        relevant_docs_count: Number(payload.relevantDocsCount ?? settings.relevant_docs_count ?? 5),
        max_messages: Number(payload.maxMessages ?? settings.max_messages ?? 15),
        num_examples: Number(payload.numExamples ?? settings.num_examples ?? 2),
        max_commands: Number(payload.maxCommands ?? settings.max_commands ?? -1),
        show_command_syntax: payload.showCommandSyntax || settings.show_command_syntax || 'full',
        narrate_behavior: Boolean(payload.narrateBehavior ?? settings.narrate_behavior ?? true),
        chat_bot_messages: Boolean(payload.chatBotMessages ?? settings.chat_bot_messages ?? true),
        chat_ingame: true,
        spawn_timeout: Number(payload.spawnTimeout ?? settings.spawn_timeout ?? 30),
        block_place_delay: Number(payload.blockPlaceDelay ?? settings.block_place_delay ?? 0),
        log_all_prompts: Boolean(payload.logAllPrompts ?? settings.log_all_prompts ?? false),
        aiplayers_owner_name: String(payload.ownerName || ''),
        aiplayers_owner_uuid: String(payload.ownerUuid || ''),
    });
}

function buildAgentConnectionState(agentName, conn) {
    return {
        name: agentName,
        inGame: Boolean(conn.in_game),
        viewerPort: conn.viewer_port,
        socketConnected: Boolean(conn.socket),
        ownerName: conn.owner_name || '',
        ownerUuid: conn.owner_uuid || '',
        lastOutput: conn.last_output || '',
        lastError: conn.last_error || null,
        mindserverPort: Number(settings.mindserver_port || 8080),
    };
}

function buildAgentList() {
    return Object.entries(agent_connections).map(([agentName, conn]) => buildAgentConnectionState(agentName, conn));
}

async function buildAgentDetails(agentName) {
    const conn = agent_connections[agentName];
    if (!conn) {
        return null;
    }
    return {
        ...buildAgentConnectionState(agentName, conn),
        state: await getAgentState(agentName),
    };
}

async function buildAgentListWithState() {
    const agents = [];
    for (const agentName of Object.keys(agent_connections)) {
        agents.push(await buildAgentDetails(agentName));
    }
    return agents;
}

async function getAgentState(agentName) {
    const agent = agent_connections[agentName];
    if (!agent) {
        return null;
    }
    if (!agent.socket || !agent.in_game) {
        return agent.full_state || null;
    }
    try {
        const state = await new Promise((resolve) => {
            agent.socket.emit('get-full-state', (payload) => resolve(payload));
        });
        agent.full_state = state;
        return state;
    } catch (error) {
        agent.last_error = String(error);
        return agent.full_state || null;
    }
}

function agentsStatusUpdate(socket) {
    if (!socket) {
        socket = io;
    }
    socket.emit('agents-status', buildAgentList());
}

let listenerInterval = null;
function addListener(listenerSocket) {
    agent_listeners.push(listenerSocket);
    if (agent_listeners.length === 1) {
        listenerInterval = setInterval(async () => {
            const states = {};
            for (let agentName in agent_connections) {
                states[agentName] = await getAgentState(agentName);
            }
            for (let listener of agent_listeners) {
                listener.emit('state-update', states);
            }
        }, 1000);
    }
}

function removeListener(listenerSocket) {
    agent_listeners.splice(agent_listeners.indexOf(listenerSocket), 1);
    if (agent_listeners.length === 0) {
        clearInterval(listenerInterval);
        listenerInterval = null;
    }
}

export const getIO = () => io;
export const getServer = () => server;
export const numStateListeners = () => agent_listeners.length;
