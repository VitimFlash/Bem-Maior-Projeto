// =============================================
// ESTADO DO JOGO
// =============================================
let codigoSala = new URLSearchParams(window.location.search).get('sala');
let usernameAtual = '';
let estadoAtual = {};
let moedasTributo = 0;
let moedasBem = 0;
let totalMoedas = 5;
let stompClient = null;
let idVotacaoAtiva = null;

// =============================================
// INICIALIZAÇÃO
// =============================================
async function init() {
    const resp = await fetch('/auth/me');
    if (!resp.ok) { window.location.href = '/index.html'; return; }
    const usuario = await resp.json();
    usernameAtual = usuario.username;
    document.getElementById('nome-usuario').textContent = usernameAtual;

    // Conecta WebSocket primeiro
    conectarWebSocket();
}

function conectarWebSocket() {
    const socket = new SockJS('/ws-bemmaior');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, 
        // Sucesso
        () => {
            console.log('WebSocket conectado!');
            document.getElementById('info-fase').textContent = 'Conectado';

            stompClient.subscribe('/topic/sala/' + codigoSala, (msg) => {
                console.log('Mensagem sala:', msg.body);
                tratarMensagemSala(JSON.parse(msg.body));
            });

            stompClient.subscribe('/user/queue/estado-jogador', (msg) => {
                console.log('Mensagem jogador:', msg.body);
                tratarEstadoJogador(JSON.parse(msg.body));
            });

            // Canal personalizado de evento por jogador
           stompClient.subscribe('/user/queue/estado-jogador-evento', (msg) => {
                const dados = JSON.parse(msg.body);
                console.log('Evento personalizado:', dados);

                if (dados.fase === 'EVENTO' && dados.eventoAtualInfo) {
                    mostrarEvento(dados.eventoAtualInfo);
                }

                if (dados.fase === 'DECISAO_EVENTO_ANTES' && dados.eventoAtualInfo) {
                    // Mostra interface do evento ANTES das moedas
                    document.getElementById('painel-decisao').classList.add('escondido');
                    eventoAtual = dados.eventoAtualInfo;
                    mostrarInterfaceEventoAntes(dados.eventoAtualInfo);
                }
            });

            // Busca estado atual após conectar
            buscarEstadoAtual();
        },
        // Erro
        (erro) => {
            console.error('Erro WebSocket:', erro);
            document.getElementById('info-fase').textContent = 'Reconectando...';
            // Tenta reconectar após 3 segundos
            setTimeout(conectarWebSocket, 3000);
        }
    );
}

async function buscarEstadoAtual() {
    try {
        const resp = await fetch('/jogo/estado/' + codigoSala);
        if (resp.ok) {
            const dados = await resp.json();
            const estadoSala = dados.sala;
            const estadoJogador = dados.jogador;

            console.log('Estado sala:', estadoSala);
            console.log('Estado jogador:', estadoJogador);

            if (estadoSala && estadoSala.contasPessoais &&
                Object.keys(estadoSala.contasPessoais).length > 0) {

                estadoAtual = estadoSala;
                atualizarMesa(estadoSala);
                atualizarInfoRodada(estadoSala);

                if (estadoSala.meta) {
                    document.getElementById('meta-valor').textContent =
                        estadoSala.meta + ' 🪙';
                    document.getElementById('meta-placar-valor').textContent =
                        estadoSala.meta;
                }

                if (estadoSala.fase === 'DECISAO' ||
                    estadoSala.fase === 'EM_JOGO') {
                    mostrarFaseDecisao(estadoSala);
                }

                if (estadoJogador) {
                    totalMoedas = estadoJogador.moedasDaRodada || 5;
                }

            } else {
                console.log('Estado vazio, tentando novamente...');
                setTimeout(buscarEstadoAtual, 2000);
            }
        } else {
            console.log('Endpoint retornou:', resp.status);
            setTimeout(buscarEstadoAtual, 2000);
        }
    } catch(e) {
        console.error('Erro buscarEstadoAtual:', e);
        setTimeout(buscarEstadoAtual, 2000);
    }
}

// =============================================
// TRATAMENTO DE MENSAGENS
// =============================================
function tratarMensagemSala(dados) {
    console.log('Mensagem recebida:', dados);

    const tipo = dados.tipo || dados.fase;

    switch(tipo) {
        case 'JOGO_INICIADO':
            // Jogo iniciado — aguarda o EstadoSala que vem logo depois
            mostrarMensagemFlutuante('Jogo iniciado!');
            break;

        case 'DECISAO':
            estadoAtual = dados;
            // Remove painel de evento antes se existir
            const painelAntes = document.getElementById('painel-evento-antes');
            if (painelAntes) painelAntes.remove();

            atualizarMesa(dados);
            mostrarFaseDecisao(dados);
            atualizarInfoRodada(dados);

            // Se tem evento que decide depois, mostra no painel extra
            if (dados.decisaoEventoDepois && dados.eventoAtualInfo) {
                eventoAtual = dados.eventoAtualInfo;
                setTimeout(() => {
                    mostrarInterfaceEvento(dados.eventoAtualInfo);
                }, 500);
            }
            break;

        case 'EM_JOGO':
            estadoAtual = dados;
            atualizarMesa(dados);
            mostrarFaseDecisao(dados);
            atualizarInfoRodada(dados);
            break;

        case 'REVELACAO':
            estadoAtual = dados;
            atualizarMesa(dados);
            mostrarRevelacao(dados);
            atualizarInfoRodada(dados);
            break;

        case 'EVENTO':
            mostrarEvento(dados.evento);
            break;

        case 'VOTACAO_ABERTA':
            idVotacaoAtiva = dados.idVotacao;
            mostrarVotacao(dados.jogadorAlvo);
            break;

        case 'VOTACAO_ATUALIZADA':
            atualizarVotacao(dados);
            break;

        case 'VOTACAO_CONCLUIDA':
            concluirVotacao(dados);
            break;

        case 'FIM':
        case 'FINALIZADA':
            window.location.href = '/resultado.html?sala=' + codigoSala;
            break;

        case 'DISCUSSAO':
            estadoAtual = dados;
            atualizarMesa(dados);
            atualizarInfoRodada(dados);
            // Só mostra opção de votar se podeVotar for true
            mostrarFaseDiscussao(dados);
            break;

        case 'RESULTADO_VOTACAO':
            estadoAtual = dados;
            // Atualiza mesa — jogador eliminado ficará transparente
            atualizarMesa(dados);
            mostrarMensagemFlutuante(dados.mensagem || '');
            if (dados.jogadorEliminado) {
                marcarJogadorEliminado(dados.jogadorEliminado);
            }
            break;

        case 'EVENTO':
            estadoAtual = dados;
            atualizarMesa(dados);
            atualizarInfoRodada(dados);
            if (dados.eventoAtualInfo) {
                mostrarEvento(dados.eventoAtualInfo);
            }
            break;

        case 'REVELACAO':
            estadoAtual = dados;
            atualizarMesa(dados);
            mostrarRevelacao(dados);
            atualizarInfoRodada(dados);
            // Esconde painel de decisão
            document.getElementById('painel-decisao').classList.add('escondido');
            break;

        default:
            // Tenta tratar como EstadoSala genérico
            if (dados.contasPessoais) {
                estadoAtual = dados;
                atualizarMesa(dados);
                atualizarInfoRodada(dados);
                if (dados.fase === 'DECISAO' || dados.fase === 'EM_JOGO') {
                    mostrarFaseDecisao(dados);
                }
            }
            break;
    }

    if (dados.mensagem) mostrarMensagemFlutuante(dados.mensagem);
    if (dados.meta) {
        document.getElementById('meta-valor').textContent = dados.meta + ' 🪙';
        document.getElementById('meta-placar-valor').textContent = dados.meta;
    }
}

function atualizarInfoRodada(dados) {
    if (dados.rodadaAtual) {
        document.getElementById('info-rodada').textContent =
            'Rodada ' + dados.rodadaAtual + '/' + dados.totalRodadas;
    }
    if (dados.fase) {
        const fases = {
            'DECISAO': 'Decidindo',
            'EM_JOGO': 'Decidindo',
            'REVELACAO': 'Revelação',
            'VOTACAO': 'Votação',
            'EVENTO': 'Evento',
            'FIM': 'Fim de Jogo'
        };
        document.getElementById('info-fase').textContent =
            fases[dados.fase] || dados.fase;
        document.getElementById('info-fase').className =
            'badge-fase fase-' + dados.fase.toLowerCase();
    }
}

function tratarEstadoJogador(dados) {
    totalMoedas = dados.moedasDaRodada || 5;
}

// =============================================
// MESA CIRCULAR
// =============================================
function atualizarMesa(estado) {
    const todasContas = estado.contasPessoais || {};
    const jogadores = Object.keys(todasContas);
    const container = document.getElementById('jogadores-container');
    container.innerHTML = '';
    const raio = 220;

    jogadores.forEach((nome, i) => {
        const angulo = (2 * Math.PI * i / jogadores.length) - Math.PI / 2;
        const x = raio * Math.cos(angulo);
        const y = raio * Math.sin(angulo);
        const jaDecidiu = (estado.jaDecidiram || []).includes(nome);
        const souEu = nome === usernameAtual;
        const eliminado = (estado.jogadoresEliminados || []).includes(nome);

        const card = document.createElement('div');
        card.className = 'jogador-card' +
            (souEu ? ' eu' : '') +
            (jaDecidiu ? ' decidido' : '') +
            (eliminado ? ' eliminado-permanente' : '');
        card.style.transform = `translate(${x}px, ${y}px)`;
        card.id = 'jogador-' + nome;

        card.innerHTML = `
            <div class="jogador-avatar"
                style="${eliminado ? 'background:var(--perigo); opacity:0.5' : ''}">
                ${nome.charAt(0).toUpperCase()}
            </div>
            <div class="jogador-nome">
                ${nome}${souEu ? ' (você)' : ''}
                ${eliminado ? ' ☠️' : ''}
            </div>
            <div class="jogador-conta">
                💰 ${todasContas[nome]} moedas
            </div>
            ${jaDecidiu && !eliminado ? '<div class="decidido-badge">✅</div>' : ''}
        `;
        container.appendChild(card);
    });

    document.getElementById('tributos-valor').textContent =
        (estado.totalTributosRodada || 0) + ' 🪙';
    atualizarPlacar(estado);
}

function marcarJogadorEliminado(nomeJogador) {
    const card = document.getElementById('jogador-' + nomeJogador);
    if (card) {
        card.classList.add('eliminado-permanente');
    }
}

// =============================================
// PLACAR
// =============================================
function atualizarPlacar(estado) {
    const lista = document.getElementById('lista-placar');
    lista.innerHTML = '';
    const meta = estado.meta || 0;

    Object.entries(estado.contasPessoais || {})
        .sort((a, b) => b[1] - a[1])
        .forEach(([nome, valor]) => {
            const item = document.createElement('div');
            item.className = 'placar-item' + (nome === usernameAtual ? ' eu' : '');
            item.innerHTML = `
                <span class="placar-nome">${nome}</span>
                <span class="placar-valor ${valor >= meta ? 'acima-meta' : ''}">
                    ${valor} 🪙
                </span>
            `;
            lista.appendChild(item);
        });
}

// =============================================
// FASE DE DECISÃO
// =============================================
function mostrarFaseDecisao(dados) {
    // Verifica se o jogador atual está eliminado
    const eliminados = dados.jogadoresEliminados || [];
    if (eliminados.includes(usernameAtual)) {
        // Jogador eliminado — mostra como espectador
        document.getElementById('painel-decisao').classList.add('escondido');
        mostrarMensagemFlutuante('☠️ Você foi eliminado. Modo espectador ativado.');
        document.getElementById('info-fase').textContent = '☠️ Espectador';
        return;
    }

    moedasTributo = 0;
    moedasBem = 0;
    totalMoedas = dados.moedasDaRodada || 5;
    atualizarContadores();
    document.getElementById('painel-decisao').classList.remove('escondido');
    document.getElementById('btn-confirmar').disabled = false;
    document.getElementById('btn-confirmar').textContent = 'Confirmar';
    document.getElementById('info-fase').textContent = 'Decidindo';
    document.getElementById('info-fase').className = 'badge-fase fase-decisao';
}

function ajustarMoeda(tipo, delta) {
    if (tipo === 'tributo') {
        const novo = moedasTributo + delta;
        if (novo < 0 || novo > totalMoedas - moedasBem) return;
        moedasTributo = novo;
    } else {
        const novo = moedasBem + delta;
        if (novo < 0 || novo > totalMoedas - moedasTributo) return;
        moedasBem = novo;
    }
    atualizarContadores();
}

function atualizarContadores() {
    document.getElementById('moedas-tributo').textContent = moedasTributo;
    document.getElementById('moedas-bem').textContent = moedasBem;
    document.getElementById('moedas-restantes').textContent =
        totalMoedas - moedasTributo - moedasBem;
}

async function confirmarDecisao() {
    const restantes = totalMoedas - moedasTributo - moedasBem;
    if (restantes !== 0) {
        document.getElementById('decisao-erro').textContent =
            'Distribua todas as ' + totalMoedas + ' moedas!';
        return;
    }
    stompClient.send('/app/jogo/' + codigoSala + '/decisao', {},
        JSON.stringify({ moedasTributo, moedasBemPessoal: moedasBem })
    );
    document.getElementById('btn-confirmar').disabled = true;
    document.getElementById('btn-confirmar').textContent = 'Aguardando...';
    document.getElementById('decisao-erro').textContent = '';
}

// FASE DISCUSSÃO
let intervaloDiscussao = null;

function mostrarFaseDiscussao(dados) {
    document.getElementById('painel-decisao').classList.add('escondido');
    document.getElementById('info-fase').textContent = '💬 Discussão';
    document.getElementById('info-fase').className = 'badge-fase fase-discussao';

    if (intervaloDiscussao) {
        clearInterval(intervaloDiscussao);
        intervaloDiscussao = null;
    }

    const painelExistente = document.getElementById('painel-discussao');
    if (painelExistente) painelExistente.remove();

    const tempoTotal = dados.tempoDiscussao || 30;
    let segundosRestantes = tempoTotal;

    // Verifica explicitamente se pode votar
    const podeVotar = dados.podeVotar === true;

    const jogadoresOpcoes = Object.keys(dados.contasPessoais || {})
        .filter(n => n !== usernameAtual &&
            !(dados.jogadoresEliminados || []).includes(n))
        .map(n => `<option value="${n}" style="color:black">${n}</option>`)
        .join('');

    const rodadeMetade = Math.floor((dados.totalRodadas || 0) / 2) + 1;

    const votacaoHtml = podeVotar
        ? `<div id="painel-votacao-individual" style="margin-top:12px;">
            <p style="color:var(--texto-fraco); font-size:0.85rem;
                margin-bottom:8px;">
                Escolha um jogador para eliminar:
            </p>
            <select id="select-eliminar"
                style="width:100%; padding:10px; margin-bottom:8px;
                background:white; color:black; border-radius:8px;
                font-size:0.95rem; border:none;">
                <option value="" style="color:black">
                    Escolha quem eliminar...
                </option>
                ${jogadoresOpcoes}
            </select>
            <div style="display:flex; gap:8px;">
                <button class="btn-principal" id="btn-votar-eliminar"
                    onclick="votarEliminar()"
                    style="flex:2; background:var(--perigo);">
                    🗳️ Votar para Eliminar
                </button>
                <button class="btn-principal" id="btn-pular-voto"
                    onclick="pularVoto()"
                    style="flex:1; background:rgba(255,255,255,0.15);
                    color:var(--texto);">
                    ⏭️ Pular
                </button>
            </div>
           </div>`
        : `<p style="color:var(--texto-fraco); text-align:center;
              font-size:0.82rem; margin-top:12px; padding:10px;
              background:rgba(255,255,255,0.05); border-radius:8px;">
              🔒 Votação disponível a partir da rodada ${rodadeMetade}
           </p>`;

    const painel = document.createElement('div');
    painel.id = 'painel-discussao';
    painel.className = 'painel-decisao';
    painel.innerHTML = `
        <div class="decisao-card">
            <h3>💬 Tempo de Discussão</h3>
            <div class="contagem-regressiva" id="contagem-discussao">
                ${tempoTotal}
            </div>
            <p style="color:var(--texto-fraco); text-align:center;
                margin:8px 0; font-size:0.9rem;">
                ${dados.mensagem || 'Discutam entre si!'}
            </p>
            ${votacaoHtml}
        </div>
    `;
    document.body.appendChild(painel);

    intervaloDiscussao = setInterval(() => {
        segundosRestantes--;
        const el = document.getElementById('contagem-discussao');
        if (el) {
            el.textContent = segundosRestantes;
            if (segundosRestantes <= 10) el.style.color = 'var(--perigo)';
            else if (segundosRestantes <= 20) el.style.color = 'var(--destaque)';
        }
        if (segundosRestantes <= 0) {
            clearInterval(intervaloDiscussao);
            intervaloDiscussao = null;
            const p = document.getElementById('painel-discussao');
            if (p) p.remove();
        }
    }, 1000);
}

async function votarEliminar() {
    const select = document.getElementById('select-eliminar');
    const alvo = select ? select.value : '';
    if (!alvo) {
        mostrarMensagemFlutuante('Selecione um jogador!');
        return;
    }
    await registrarVoto(alvo);
}

async function pularVoto() {
    await registrarVoto('PULAR');
}

async function registrarVoto(alvo) {
    try {
        await fetch('/votacao/votar-individual', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ codigoSala, alvo })
        });

        // Desabilita botões após votar
        const btnVotar = document.getElementById('btn-votar-eliminar');
        const btnPular = document.getElementById('btn-pular-voto');
        const select = document.getElementById('select-eliminar');
        if (btnVotar) btnVotar.disabled = true;
        if (btnPular) btnPular.disabled = true;
        if (select) select.disabled = true;

        mostrarMensagemFlutuante(
            alvo === 'PULAR' ? '⏭️ Voto pulado!' : '🗳️ Voto registrado!'
        );
    } catch(e) {
        console.error('Erro ao votar:', e);
    }
}

function mostrarPainelDiscussao(segundos, dados) {
    // Remove painel anterior se existir
    const painelExistente = document.getElementById('painel-discussao');
    if (painelExistente) painelExistente.remove();

    const painel = document.createElement('div');
    painel.id = 'painel-discussao';
    painel.className = 'painel-decisao';
    painel.innerHTML = `
        <div class="decisao-card">
            <h3>💬 Tempo de Discussão</h3>
            <div class="contagem-regressiva" id="contagem">
                ${segundos}
            </div>
            <p style="color: var(--texto-fraco); text-align:center; margin: 12px 0;">
                Discutam entre si! Você pode votar para eliminar um jogador.
            </p>
            ${dados.podeVotar ? `
            <div style="margin-top: 12px;">
                <select id="select-votar" style="width:100%; padding:10px;
                    background: rgba(255,255,255,0.07);
                    border: 1px solid rgba(255,255,255,0.15);
                    border-radius: 8px; color: var(--texto); margin-bottom: 8px;">
                    <option value="">Selecione um jogador para votar...</option>
                    ${Object.keys(dados.contasPessoais || {})
                        .filter(n => n !== usernameAtual)
                        .map(n => `<option value="${n}">${n}</option>`)
                        .join('')}
                </select>
                <button class="btn-principal" onclick="abrirVotacaoContraSelecionado()"
                    style="background: var(--perigo);">
                    🗳️ Iniciar Votação
                </button>
            </div>` : ''}
        </div>
    `;
    document.body.appendChild(painel);

    // Atualiza contagem visualmente
    const el = document.getElementById('contagem');
    if (el && segundos > 0) {
        el.textContent = segundos;
        // Muda cor quando estiver acabando
        if (segundos <= 10) el.style.color = 'var(--perigo)';
        else if (segundos <= 20) el.style.color = 'var(--destaque)';
        else el.style.color = 'var(--sucesso)';
    }

    // Remove painel quando fase mudar
    if (segundos === 0) {
        setTimeout(() => {
            const p = document.getElementById('painel-discussao');
            if (p) p.remove();
        }, 1000);
    }
}

async function abrirVotacaoContra(nomeJogador) {
    try {
        // Busca jogadores da sala para achar o ID
        const resp = await fetch('/sala/' + codigoSala + '/jogadores-detalhes');
        if (!resp.ok) {
            mostrarMensagemFlutuante('Erro ao abrir votação!');
            return;
        }
        const jogadores = await resp.json();
        const alvo = jogadores.find(j => j.username === nomeJogador);

        if (!alvo) {
            mostrarMensagemFlutuante('Jogador não encontrado!');
            return;
        }

        const respVotacao = await fetch('/votacao/abrir', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                codigoSala: codigoSala,
                idJogadorAlvo: alvo.id
            })
        });

        if (!respVotacao.ok) {
            const erro = await respVotacao.text();
            mostrarMensagemFlutuante(erro);
        }
    } catch(e) {
        console.error('Erro ao abrir votação:', e);
    }
}

// =============================================
// REVELAÇÃO
// =============================================
function mostrarRevelacao(dados) {
    document.getElementById('painel-decisao').classList.add('escondido');
    document.getElementById('info-fase').textContent = 'Revelação';
    document.getElementById('info-fase').className = 'badge-fase fase-revelacao';
    document.getElementById('btn-confirmar').disabled = false;
    document.getElementById('btn-confirmar').textContent = 'Confirmar';
    const caixa = document.getElementById('caixa-tributos');
    caixa.classList.add('revelando');
    setTimeout(() => caixa.classList.remove('revelando'), 1000);
}

// =============================================
// EVENTOS
// =============================================
function mostrarEvento(evento) {
    if (!evento) return;

    const emojis = {
        ROUBO: '💰', VENENO: '🧪', PARCEIROS: '🤝',
        ROLETA: '🎰', DUPLICATA: '✌️', EXPOSICAO: '🔍',
        LIDERANCA: '👑', OSMOSE: '🧂', TRAICAO: '🔪',
        COPIA: '📄', BOLHA: '🫧', BOMBA_RELOGIO: '💣',
        IGUALDADE: '🟰', FOGUEIRA: '🔥'
    };

    const nomes = {
        ROUBO: 'Roubo', VENENO: 'Veneno', PARCEIROS: 'Parceiros',
        ROLETA: 'Roleta', DUPLICATA: 'Duplicata', EXPOSICAO: 'Exposição',
        LIDERANCA: 'Liderança', OSMOSE: 'Osmose', TRAICAO: 'Traição',
        COPIA: 'Cópia', BOLHA: 'Bolha', BOMBA_RELOGIO: 'Bomba-Relógio',
        IGUALDADE: 'Igualdade', FOGUEIRA: 'Fogueira'
    };

    document.getElementById('evento-emoji').textContent =
        emojis[evento.tipo] || '⚡';
    document.getElementById('evento-titulo').textContent =
        nomes[evento.tipo] || evento.tipo;
    document.getElementById('evento-descricao').textContent =
        evento.descricao || '';

    // Dispara animação
    dispararAnimacaoEvento(evento.tipo);

    // Mostra overlay
    document.getElementById('overlay-evento').classList.remove('escondido');

    // Fecha após 8 segundos e mostra interface
    setTimeout(() => {
        fecharEvento();
        eventoAtual = evento;
        // Mostra interface do evento no painel de decisão
        if (evento.requerDecisao) {
            mostrarInterfaceEvento(evento);
        }
    }, 8000);
}


function obterEmojiEvento(tipo) {
    const emojis = {
        ROUBO: '💰', VENENO: '🧪', PARCEIROS: '🤝',
        ROLETA: '🎰', DUPLICATA: '✌️', EXPOSICAO: '🔍',
        LIDERANCA: '👑', OSMOSE: '🧂', TRAICAO: '🔪',
        COPIA: '📄', BOLHA: '🫧', BOMBA_RELOGIO: '💣',
        IGUALDADE: '🟰', FOGUEIRA: '🔥'
    };
    return emojis[tipo] || '⚡';
}


function fecharEvento() {
    document.getElementById('overlay-evento').classList.add('escondido');
    document.getElementById('evento-animacao').innerHTML = '';
}

function dispararAnimacaoEvento(tipo) {
    // Garante que o container existe
    let container = document.getElementById('evento-animacao');
    if (!container) return;
    container.innerHTML = '';

    // Pequeno delay para garantir que o overlay está visível
    setTimeout(() => {
        const animacoes = {
            ROUBO:         () => animacaoCedulasCaindo(container),
            VENENO:        () => animacaoCaveirasSubindo(container),
            PARCEIROS:     () => animacaoBrilhoAmarelo(container),
            ROLETA:        () => animacaoSetasRoleta(container),
            DUPLICATA:     () => animacaoCachoeiraMoedas(container),
            EXPOSICAO:     () => animacaoZoom(container),
            LIDERANCA:     () => animacaoCoroacao(container),
            OSMOSE:        () => animacaoEspadasX(container),
            TRAICAO:       () => animacaoFacaGirando(container),
            COPIA:         () => animacaoPrintTela(container),
            BOLHA:         () => animacaoBolhasEstourando(container),
            BOMBA_RELOGIO: () => animacaoExplosao(container),
            IGUALDADE:     () => animacaoMatematica(container),
            FOGUEIRA:      () => animacaoFogo(container),
        };
        if (animacoes[tipo]) {
            animacoes[tipo]();
            console.log('Animação disparada:', tipo);
        }
    }, 100);
}
// =============================================
// ANIMAÇÕES
// =============================================
function animacaoCedulasCaindo(c) {
    for (let i = 0; i < 12; i++) {
        const el = document.createElement('div');
        el.className = 'anim-cedula';
        el.textContent = '💵';
        el.style.left = Math.random() * 100 + '%';
        el.style.animationDelay = Math.random() * 1.5 + 's';
        el.style.fontSize = (16 + Math.random() * 16) + 'px';
        c.appendChild(el);
    }
}
function animacaoCaveirasSubindo(c) {
    for (let i = 0; i < 8; i++) {
        const el = document.createElement('div');
        el.className = 'anim-caveira';
        el.textContent = '💀';
        el.style.left = Math.random() * 100 + '%';
        el.style.animationDelay = Math.random() * 1.5 + 's';
        el.style.opacity = '0.5';
        c.appendChild(el);
    }
}
function animacaoBrilhoAmarelo(c) {
    const el = document.createElement('div');
    el.className = 'anim-brilho';
    c.appendChild(el);
}
function animacaoSetasRoleta(c) {
    for (let i = 0; i < 5; i++) {
        const up = document.createElement('div');
        up.className = 'anim-seta-cima';
        up.textContent = '↑';
        up.style.left = (10 + i * 18) + '%';
        up.style.animationDelay = i * 0.2 + 's';
        c.appendChild(up);
        const down = document.createElement('div');
        down.className = 'anim-seta-baixo';
        down.textContent = '↓';
        down.style.left = (10 + i * 18) + '%';
        down.style.animationDelay = i * 0.2 + 's';
        c.appendChild(down);
    }
}
function animacaoCachoeiraMoedas(c) {
    ['esquerda','direita'].forEach(lado => {
        for (let i = 0; i < 8; i++) {
            const el = document.createElement('div');
            el.className = 'anim-moeda-' + lado;
            el.textContent = '🪙';
            el.style.top = (i * 12) + '%';
            el.style.animationDelay = i * 0.15 + 's';
            c.appendChild(el);
        }
    });
}
function animacaoZoom(c) {
    const el = document.createElement('div');
    el.className = 'anim-zoom';
    el.textContent = '🔍';
    c.appendChild(el);
}
function animacaoCoroacao(c) {
    const el = document.createElement('div');
    el.className = 'anim-coroa';
    el.textContent = '👑';
    c.appendChild(el);
}
function animacaoEspadasX(c) {
    const el = document.createElement('div');
    el.className = 'anim-espadas';
    el.innerHTML = '<span class="espada-esq">⚔️</span><span class="espada-dir">⚔️</span>';
    c.appendChild(el);
}
function animacaoFacaGirando(c) {
    const el = document.createElement('div');
    el.className = 'anim-faca';
    el.textContent = '🔪';
    c.appendChild(el);
}
function animacaoPrintTela(c) {
    const el = document.createElement('div');
    el.className = 'anim-print';
    c.appendChild(el);
}
function animacaoBolhasEstourando(c) {
    for (let i = 0; i < 10; i++) {
        const el = document.createElement('div');
        el.className = 'anim-bolha';
        el.textContent = '🫧';
        el.style.left = Math.random() * 100 + '%';
        el.style.top = Math.random() * 100 + '%';
        el.style.animationDelay = Math.random() * 1.5 + 's';
        c.appendChild(el);
    }
}
function animacaoExplosao(c) {
    const el = document.createElement('div');
    el.className = 'anim-explosao';
    el.textContent = '💥';
    c.appendChild(el);
}
function animacaoMatematica(c) {
    const simbolos = ['1','2','+','=','3','÷','×','7','-','4'];
    for (let i = 0; i < 20; i++) {
        const el = document.createElement('div');
        el.className = 'anim-matematica';
        el.textContent = simbolos[Math.floor(Math.random() * simbolos.length)];
        el.style.left = Math.random() * 100 + '%';
        el.style.animationDelay = Math.random() + 's';
        el.style.fontSize = (12 + Math.random() * 20) + 'px';
        c.appendChild(el);
    }
}
function animacaoFogo(c) {
    for (let i = 0; i < 8; i++) {
        const el = document.createElement('div');
        el.className = 'anim-fogo';
        el.textContent = '🔥';
        el.style.left = (i * 13) + '%';
        el.style.animationDelay = i * 0.1 + 's';
        el.style.fontSize = (20 + Math.random() * 20) + 'px';
        c.appendChild(el);
    }
}

// Dados do evento atual
let eventoAtual = null;

function mostrarInterfaceEvento(evento, containerOverride) {
    eventoAtual = evento;
    const painel = containerOverride ||
        document.getElementById('painel-evento-extra');
    if (!painel) return;
    painel.innerHTML = '';
    if (!containerOverride) painel.classList.remove('escondido');

    switch(evento.tipo) {
        case 'ROUBO':         mostrarInterfaceRoubo(evento, painel); break;
        case 'VENENO':        mostrarInterfaceVeneno(evento, painel); break;
        case 'PARCEIROS':     mostrarInterfaceParceiros(evento, painel); break;
        case 'ROLETA':        mostrarInterfaceRoleta(evento, painel); break;
        case 'DUPLICATA':     mostrarInterfaceDuplicata(evento, painel); break;
        case 'EXPOSICAO':     mostrarInterfaceExposicao(evento, painel); break;
        case 'LIDERANCA':     mostrarInterfaceLideranca(evento, painel); break;
        case 'OSMOSE':        mostrarInterfaceOsmose(evento, painel); break;
        case 'TRAICAO':       mostrarInterfaceTraicao(evento, painel); break;
        case 'COPIA':         mostrarInterfaceCopia(evento, painel); break;
        case 'IGUALDADE':     mostrarInterfaceIgualdade(evento, painel); break;
        case 'BOMBA_RELOGIO': mostrarInterfaceBomba(evento, painel); break;
    }
}

function mostrarInterfaceEventoAntes(evento) {
    // Remove painel anterior
    const painelExistente = document.getElementById('painel-evento-antes');
    if (painelExistente) painelExistente.remove();

    const painel = document.createElement('div');
    painel.id = 'painel-evento-antes';
    painel.className = 'painel-decisao';
    painel.innerHTML = `
        <div class="decisao-card">
            <h3>${obterEmojiEvento(evento.tipo)} Decisão do Evento</h3>
            <div id="conteudo-evento-antes"></div>
        </div>
    `;
    document.body.appendChild(painel);

    const conteudo = document.getElementById('conteudo-evento-antes');
    mostrarInterfaceEvento(evento, conteudo);
}

// =============================================
// INTERFACES DOS EVENTOS
// =============================================

function seletorJogadores(excluirEuMesmo = true) {
    const jogadores = Object.keys(estadoAtual.contasPessoais || {});
    const opcoes = jogadores
        .filter(n => excluirEuMesmo ? n !== usernameAtual : true)
        .map(n => `<option value="${n}">${n}</option>`)
        .join('');
    return `<select class="select-evento" id="select-alvo-evento">
        <option value="">Escolha um jogador...</option>
        ${opcoes}
    </select>`;
}

function mostrarInterfaceRoubo(evento, painel) {
    if (evento.jogadorAlvoId !== usernameAtual &&
        !evento.executorId?.includes(usernameAtual)) {
        painel.innerHTML = `<p class="evento-info-neutro">
            👀 Aguardando o ladrão escolher sua vítima...
        </p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>💰 Você é o <strong>Ladrão</strong>! Escolha quem roubar
               (ou não roube ninguém):</p>
            ${seletorJogadores()}
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="confirmarRoubo()">
                    💰 Roubar
                </button>
                <button class="btn-evento-neutro" onclick="recusarRoubo()">
                    Não roubar ninguém
                </button>
            </div>
        </div>
    `;
}

async function confirmarRoubo() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'ROUBO', acao: 'ROUBAR', alvo });
    document.getElementById('painel-evento-extra').innerHTML =
        '<p class="evento-info-neutro">✅ Ação registrada!</p>';
}

async function recusarRoubo() {
    await enviarAcaoEvento({ tipo: 'ROUBO', acao: 'RECUSAR' });
    document.getElementById('painel-evento-extra').innerHTML =
        '<p class="evento-info-neutro">✅ Você optou por não roubar.</p>';
}

function mostrarInterfaceVeneno(evento, painel) {
    if (!evento.souFeiticeiro) {
        painel.innerHTML = `<p class="evento-info-neutro">
            🧪 Aguardando o Feiticeiro escolher sua vítima...
        </p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🧪 Você é o <strong>Feiticeiro</strong>! Escolha quem envenenar:</p>
            <p class="evento-aviso">Se a vítima não colocar 5 moedas nos tributos,
               perderá 5 moedas do bem-pessoal!</p>
            ${seletorJogadores()}
            <button class="btn-evento-acao" onclick="confirmarVeneno()">
                🧪 Envenenar
            </button>
        </div>
    `;
}

async function confirmarVeneno() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'VENENO', acao: 'ENVENENAR', alvo });
    document.getElementById('painel-evento-extra').innerHTML =
        '<p class="evento-info-neutro">🧪 Veneno preparado!</p>';
}

function mostrarInterfaceParceiros(evento, painel) {
    if (!evento.souParceiro) {
        painel.innerHTML = `<p class="evento-info-neutro">
            🤝 Aguardando os parceiros decidirem...
        </p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🤝 Você é <strong>Parceiro</strong>! Escolha sua ação:</p>
            <p class="evento-aviso">
                Ambos compartilham → bem-pessoal ×1.5 para os dois<br>
                Você engana, outro compartilha → seu bem-pessoal ×2<br>
                Ambos enganam → nada acontece
            </p>
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="escolherParceiro('COMPARTILHAR')">
                    🤝 Compartilhar
                </button>
                <button class="btn-evento-perigo" onclick="escolherParceiro('ENGANAR')">
                    😈 Enganar
                </button>
            </div>
        </div>
    `;
}

async function escolherParceiro(acao) {
    await enviarAcaoEvento({ tipo: 'PARCEIROS', acao });
    document.getElementById('painel-evento-extra').innerHTML =
        `<p class="evento-info-neutro">✅ Você escolheu: ${acao}</p>`;
}

function mostrarInterfaceRoleta(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🎰 <strong>Roleta!</strong> Você pode girar até 3 vezes:</p>
            <p class="evento-aviso">
                Cair em 1 ou 8 → +5 moedas no bem-pessoal<br>
                Qualquer outro → -5 moedas no bem-pessoal
            </p>
            <div id="roleta-resultado" style="font-size:2rem; text-align:center;
                margin:12px 0; min-height:40px;"></div>
            <div id="roleta-saldo" style="text-align:center; color:var(--destaque);
                margin-bottom:8px;"></div>
            <div class="evento-botoes">
                <button class="btn-evento-acao" id="btn-girar"
                    onclick="girarRoleta()">
                    🎰 Girar (3 restantes)
                </button>
                <button class="btn-evento-neutro" onclick="pararRoleta()">
                    Parar de girar
                </button>
            </div>
        </div>
    `;
    window.girosRoleta = 0;
    window.saldoRoleta = 0;
}

async function girarRoleta() {
    if (window.girosRoleta >= 3) return;
    window.girosRoleta++;
    const restantes = 3 - window.girosRoleta;

    const resultado = Math.floor(Math.random() * 8) + 1;
    const ganhou = resultado === 1 || resultado === 8;
    const efeito = ganhou ? +5 : -5;
    window.saldoRoleta += efeito;

    document.getElementById('roleta-resultado').innerHTML =
        `<span style="color:${ganhou ? 'var(--sucesso)' : 'var(--perigo)'}">
            ${resultado} ${ganhou ? '✅ +5' : '❌ -5'}
        </span>`;
    document.getElementById('roleta-saldo').textContent =
        `Saldo desta rodada: ${window.saldoRoleta > 0 ? '+' : ''}${window.saldoRoleta} 🪙`;

    const btn = document.getElementById('btn-girar');
    if (btn) btn.textContent = `🎰 Girar (${restantes} restantes)`;

    await enviarAcaoEvento({
        tipo: 'ROLETA', acao: 'GIRAR', resultado, efeito
    });

    if (restantes === 0) {
        if (btn) btn.disabled = true;
    }
}

async function pararRoleta() {
    await enviarAcaoEvento({ tipo: 'ROLETA', acao: 'PARAR' });
    document.getElementById('painel-evento-extra').innerHTML =
        `<p class="evento-info-neutro">✅ Roleta encerrada!
        Saldo: ${window.saldoRoleta > 0 ? '+' : ''}${window.saldoRoleta} 🪙</p>`;
}

function mostrarInterfaceDuplicata(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>✌️ <strong>Duplicata!</strong> Após confirmar suas moedas,
               escolha uma opção:</p>
            <p class="evento-aviso">50% de chance de ×2 ou ÷2</p>
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="escolherDuplicata('TRIBUTO')">
                    🏛️ Duplicar Tributos
                </button>
                <button class="btn-evento-acao" onclick="escolherDuplicata('BEM_PESSOAL')">
                    💼 Duplicar Bem-pessoal
                </button>
                <button class="btn-evento-neutro" onclick="escolherDuplicata('NENHUM')">
                    Nenhuma
                </button>
            </div>
        </div>
    `;
}

async function escolherDuplicata(opcao) {
    await enviarAcaoEvento({ tipo: 'DUPLICATA', acao: opcao });
    document.getElementById('painel-evento-extra').innerHTML =
        `<p class="evento-info-neutro">✅ Duplicata registrada!</p>`;
}

function mostrarInterfaceExposicao(evento, painel) {
    if (!evento.souExpositor) {
        painel.innerHTML = `<p class="evento-info-neutro">
            🔍 O Expositor está espiando alguém...
        </p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🔍 Você é o <strong>Expositor</strong>!
               Escolha quem espiar (opcional):</p>
            ${seletorJogadores(false)}
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="confirmarExposicao()">
                    🔍 Espiar
                </button>
                <button class="btn-evento-neutro" onclick="recusarExposicao()">
                    Não espiar ninguém
                </button>
            </div>
            <div id="resultado-exposicao" style="margin-top:12px;"></div>
        </div>
    `;
}

async function confirmarExposicao() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    const resp = await enviarAcaoEvento({ tipo: 'EXPOSICAO', acao: 'ESPIAR', alvo });
    if (resp && resp.bemPessoal !== undefined) {
        document.getElementById('resultado-exposicao').innerHTML =
            `<p style="color:var(--destaque)">
                💼 ${alvo} tem <strong>${resp.bemPessoal} moedas</strong>
                no bem-pessoal!
            </p>`;
    }
}

async function recusarExposicao() {
    await enviarAcaoEvento({ tipo: 'EXPOSICAO', acao: 'RECUSAR' });
    document.getElementById('painel-evento-extra').innerHTML =
        '<p class="evento-info-neutro">✅ Você optou por não espiar.</p>';
}

function mostrarInterfaceLideranca(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>👑 <strong>Liderança!</strong> Vote em quem deve ser o Líder:</p>
            ${seletorJogadores(false)}
            <button class="btn-evento-acao" onclick="votarLider()">
                👑 Votar
            </button>
            <div id="placar-lideranca" style="margin-top:12px;
                color:var(--texto-fraco); font-size:0.85rem;"></div>
        </div>
    `;
}

async function votarLider() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'LIDERANCA', acao: 'VOTAR', alvo });
    document.getElementById('painel-evento-extra').innerHTML =
        `<p class="evento-info-neutro">✅ Você votou em ${alvo}!</p>`;
}

function mostrarInterfaceOsmose(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🧂 <strong>Osmose!</strong> Desafie alguém para um duelo
               (após confirmar moedas):</p>
            <p class="evento-aviso">Quem tiver mais bem-pessoal rouba 3 moedas do outro.</p>
            ${seletorJogadores()}
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="confirmarOsmose()">
                    ⚔️ Desafiar
                </button>
                <button class="btn-evento-neutro" onclick="recusarOsmose()">
                    Não desafiar
                </button>
            </div>
        </div>
    `;
}

async function confirmarOsmose() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    const resp = await enviarAcaoEvento({ tipo: 'OSMOSE', acao: 'DESAFIAR', alvo });
    let msg = '⚔️ Duelo realizado!';
    if (resp) {
        if (resp.resultado === 1) msg = '⚔️ Você venceu o duelo! +3 moedas!';
        else if (resp.resultado === -1) msg = '⚔️ Você perdeu o duelo! -3 moedas.';
        else msg = '⚔️ Empate! Nada aconteceu.';
    }
    document.getElementById('painel-evento-extra').innerHTML =
        `<p class="evento-info-neutro">${msg}</p>`;
}

async function recusarOsmose() {
    await enviarAcaoEvento({ tipo: 'OSMOSE', acao: 'RECUSAR' });
    document.getElementById('painel-evento-extra').innerHTML =
        '<p class="evento-info-neutro">✅ Você optou por não desafiar.</p>';
}

function mostrarInterfaceTraicao(evento, painel) {
    if (evento.souTraidor) {
        painel.innerHTML = `
            <div class="evento-interface">
                <p>🔪 Você é o <strong>Traidor</strong>!
                   Escolha quem trair:</p>
                ${seletorJogadores()}
                <button class="btn-evento-perigo" onclick="confirmarTraicao()">
                    🔪 Trair
                </button>
            </div>
        `;
    } else if (evento.souVitima) {
        painel.innerHTML = `
            <div class="evento-interface">
                <p>🔪 Você foi <strong>traído</strong>!
                   Tente adivinhar quem foi o traidor:</p>
                ${seletorJogadores()}
                <button class="btn-evento-acao" onclick="adivinharTraidor()">
                    🔍 Acusar
                </button>
            </div>
        `;
    } else {
        painel.innerHTML = `<p class="evento-info-neutro">
            🔪 Um traidor está agindo nas sombras...
        </p>`;
    }
}

async function confirmarTraicao() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'TRAICAO', acao: 'TRAIR', alvo });
    document.getElementById('painel-evento-extra').innerHTML =
        '<p class="evento-info-neutro">🔪 Traição registrada!</p>';
}

async function adivinharTraidor() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    const resp = await enviarAcaoEvento({ tipo: 'TRAICAO', acao: 'ADIVINHAR', alvo });
    const msg = resp?.acertou
        ? '🎯 Você acertou! O traidor perdeu 3 moedas!'
        : '❌ Você errou! Perdeu 3 moedas.';
    document.getElementById('painel-evento-extra').innerHTML =
        `<p class="evento-info-neutro">${msg}</p>`;
}

function mostrarInterfaceCopia(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>📄 <strong>Cópia!</strong> Após confirmar moedas,
               você pode copiar o bem-pessoal de alguém:</p>
            ${seletorJogadores()}
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="confirmarCopia()">
                    📄 Copiar
                </button>
                <button class="btn-evento-neutro" onclick="recusarCopia()">
                    Não copiar ninguém
                </button>
            </div>
        </div>
    `;
}

async function confirmarCopia() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'COPIA', acao: 'COPIAR', alvo });
    document.getElementById('painel-evento-extra').innerHTML =
        `<p class="evento-info-neutro">📄 Bem-pessoal de ${alvo} copiado!</p>`;
}

async function recusarCopia() {
    await enviarAcaoEvento({ tipo: 'COPIA', acao: 'RECUSAR' });
    document.getElementById('painel-evento-extra').innerHTML =
        '<p class="evento-info-neutro">✅ Você optou por não copiar.</p>';
}

function mostrarInterfaceIgualdade(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🟰 <strong>Igualdade!</strong> Votem: devemos igualar
               todos os bem-pessoais?</p>
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="votarIgualdade(true)">
                    ✅ Sim, igualar
                </button>
                <button class="btn-evento-perigo" onclick="votarIgualdade(false)">
                    ❌ Não igualar
                </button>
            </div>
            <div id="placar-igualdade" style="margin-top:12px;
                color:var(--texto-fraco);"></div>
        </div>
    `;
}

async function votarIgualdade(sim) {
    await enviarAcaoEvento({ tipo: 'IGUALDADE', acao: sim ? 'SIM' : 'NAO' });
    document.getElementById('painel-evento-extra').innerHTML =
        `<p class="evento-info-neutro">✅ Voto registrado!</p>`;
}

function mostrarInterfaceBomba(evento, painel) {
    if (!evento.souPortador) {
        painel.innerHTML = `<p class="evento-info-neutro">
            💣 A bomba está passando de mão em mão...
        </p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>💣 Você está com a <strong>Bomba-Relógio</strong>!
               Passe para alguém antes que exploda!</p>
            ${seletorJogadores()}
            <button class="btn-evento-perigo" onclick="passarBomba()">
                💣 Passar a Bomba
            </button>
        </div>
    `;
}

async function passarBomba() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    const resp = await enviarAcaoEvento({ tipo: 'BOMBA_RELOGIO', acao: 'PASSAR', alvo });
    if (resp?.explodiu) {
        document.getElementById('painel-evento-extra').innerHTML =
            '<p class="evento-info-neutro">💥 A bomba explodiu!</p>';
    } else {
        document.getElementById('painel-evento-extra').innerHTML =
            `<p class="evento-info-neutro">💣 Bomba passada para ${alvo}!</p>`;
    }
}

// Envia ação do evento para o servidor
async function enviarAcaoEvento(dados) {
    try {
        const resp = await fetch('/jogo/evento/acao', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                codigoSala,
                ...dados
            })
        });
        if (resp.ok) return await resp.json();
    } catch(e) {
        console.error('Erro ao enviar ação de evento:', e);
    }
    return null;
}

// =============================================
// VOTAÇÃO
// =============================================
function mostrarVotacao(jogadorAlvo) {
    document.getElementById('votacao-descricao').textContent =
        'Votar para eliminar ' + jogadorAlvo + '?';
    document.getElementById('votos-favor').textContent = '0';
    document.getElementById('votos-contra').textContent = '0';
    document.getElementById('votacao-status').textContent = '';
    document.getElementById('overlay-votacao').classList.remove('escondido');
}

function atualizarVotacao(dados) {
    document.getElementById('votos-favor').textContent = dados.votosFavor;
    document.getElementById('votos-contra').textContent = dados.votosContra;
}

function concluirVotacao(dados) {
    const status = dados.eliminado
        ? dados.jogadorAlvo + ' foi eliminado!'
        : dados.jogadorAlvo + ' foi mantido no jogo.';
    document.getElementById('votacao-status').textContent = status;
    setTimeout(() => {
        document.getElementById('overlay-votacao').classList.add('escondido');
    }, 3000);
}

async function votar(favor) {
    await fetch('/votacao/votar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            idVotacao: idVotacaoAtiva,
            favor: favor,
            codigoSala: codigoSala
        })
    });
    document.querySelector('.votacao-botoes').innerHTML =
        '<p class="mensagem-sucesso">Voto registrado!</p>';
}

async function abrirVotacaoContra(nomeJogador) {
    const resp = await fetch('/sala/' + codigoSala + '/jogadores');
    const jogadores = await resp.json();
    mostrarMensagemFlutuante('Votação aberta contra ' + nomeJogador + '!');
}

// =============================================
// MENSAGEM FLUTUANTE
// =============================================
function mostrarMensagemFlutuante(texto) {
    const el = document.getElementById('mensagem-flutuante');
    el.textContent = texto;
    el.classList.remove('escondido');
    el.classList.add('visivel');
    setTimeout(() => {
        el.classList.remove('visivel');
        el.classList.add('escondido');
    }, 3000);
}

// Inicia
init();