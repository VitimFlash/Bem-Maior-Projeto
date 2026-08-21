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
let jaConfirmouDecisao = false;
let jaMostrouDecisaoEvento = false;
let eventoAtual = null;
let intervaloDiscussao = null;

// =============================================
// INICIALIZAÇÃO
// =============================================
async function init() {
    const resp = await fetch('/auth/me');
    if (!resp.ok) { window.location.href = '/index.html'; return; }
    const usuario = await resp.json();
    usernameAtual = usuario.username;
    document.getElementById('nome-usuario').textContent = usernameAtual;
    conectarWebSocket();
}

function conectarWebSocket() {
    const socket = new SockJS('/ws-bemmaior');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({},
        () => {
            console.log('WebSocket conectado!');
            document.getElementById('info-fase').textContent = 'Conectado';

            stompClient.subscribe('/topic/sala/' + codigoSala, (msg) => {
                console.log('Mensagem sala:', msg.body);
                tratarMensagemSala(JSON.parse(msg.body));
            });

            stompClient.subscribe('/user/queue/estado-jogador', (msg) => {
                tratarEstadoJogador(JSON.parse(msg.body));
            });

            stompClient.subscribe('/user/queue/estado-jogador-evento', (msg) => {
                const dados = JSON.parse(msg.body);
                console.log('Evento personalizado:', dados);

                if (dados.fase === 'EVENTO' && dados.eventoAtualInfo) {
                    mostrarEvento(dados.eventoAtualInfo);
                }

                if (dados.fase === 'DECISAO_EVENTO_ANTES' && dados.eventoAtualInfo) {
                    document.getElementById('painel-decisao').classList.add('escondido');
                    eventoAtual = dados.eventoAtualInfo;
                    eventoPendente = true;

                    // Guarda o estado da sala para usar após o evento
                    if (dados.contasPessoais) {
                        estadoAtual = dados;
                        estadoAtual.fase = 'DECISAO'; // marca para mostrar decisão depois
                    }

                    mostrarInterfaceEventoAntes(dados.eventoAtualInfo);
                }

                if (dados.fase === 'LIDERANCA_DISTRIBUIR') {
                    mostrarInterfaceDistribuicaoLider(dados);
                }
            });

            buscarEstadoAtual();
        },
        (erro) => {
            console.error('Erro WebSocket:', erro);
            document.getElementById('info-fase').textContent = 'Reconectando...';
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

                if (estadoSala.fase === 'DECISAO' || estadoSala.fase === 'EM_JOGO') {
                    mostrarFaseDecisao(estadoSala);
                }

                if (estadoJogador) {
                    totalMoedas = estadoJogador.moedasDaRodada || 5;
                }
            } else {
                setTimeout(buscarEstadoAtual, 2000);
            }
        } else {
            setTimeout(buscarEstadoAtual, 2000);
        }
    } catch(e) {
        console.error('Erro buscarEstadoAtual:', e);
        setTimeout(buscarEstadoAtual, 2000);
    }
}

// =============================================
// TRATAMENTO DE MENSAGENS — SEM DUPLICATAS
// =============================================
function tratarMensagemSala(dados) {
    const tipo = dados.tipo || dados.fase;

    switch(tipo) {
        case 'JOGO_INICIADO':
            mostrarMensagemFlutuante('Jogo iniciado!');
            break;

        case 'DISCUSSAO':
            // Reseta para nova rodada
            jaConfirmouDecisao = false;
            jaMostrouDecisaoEvento = false;
            eventoAtual = null;

            estadoAtual = dados;
            atualizarMesa(dados);
            atualizarInfoRodada(dados);
            mostrarFaseDiscussao(dados);
            break;

        case 'RESULTADO_VOTACAO':
            estadoAtual = dados;
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

        case 'DECISAO':
    case 'EM_JOGO':
        estadoAtual = dados;

        const painelAntes = document.getElementById('painel-evento-antes');
        if (painelAntes) painelAntes.remove();

        const painelDisc = document.getElementById('painel-discussao');
        if (painelDisc) painelDisc.remove();

        if (intervaloDiscussao) {
            clearInterval(intervaloDiscussao);
            intervaloDiscussao = null;
        }

        atualizarMesa(dados);
        atualizarInfoRodada(dados);

        if (!jaConfirmouDecisao) {
            mostrarFaseDecisao(dados);
        }

        // Evento após decisão — só mostra uma vez e não limpa
        if (dados.decisaoEventoDepois && dados.eventoAtualInfo &&
            !jaMostrouDecisaoEvento) {
            jaMostrouDecisaoEvento = true;
            eventoAtual = dados.eventoAtualInfo;
            // Guarda referência para reexibir se necessário
            window._eventoDepoisPendente = dados.eventoAtualInfo;
            setTimeout(() => mostrarInterfaceEvento(dados.eventoAtualInfo), 500);
        }
        break;

        case 'LIDERANCA_VOTOS':
            atualizarPlacarLideranca(dados.votos);
        break;

        case 'LIDER_ELEITO':
            const nomeLider = dados.lider;
            const souLider = nomeLider === usernameAtual;
            mostrarMensagemFlutuante('👑 ' + nomeLider + ' é o Líder desta rodada!');

            // Atualiza painel de evento antes para não-líderes
            const painelLiderAntes = document.getElementById('painel-evento-antes');
            if (painelLiderAntes && !souLider) {
                painelLiderAntes.innerHTML = `
                    <div class="decisao-card">
                        <h3>👑 Líder Eleito!</h3>
                        <p style="text-align:center;color:var(--destaque);font-size:1.2rem;">
                            ${nomeLider} é o Líder!
                        </p>
                        <p style="color:var(--texto-fraco);text-align:center;margin-top:8px;">
                            Aguardando o líder distribuir as moedas...
                        </p>
                    </div>
                `;
            }
        break;
        case 'RESULTADO_EVENTO':
             mostrarResultadoEvento(dados);
            break;
        case 'REVELACAO':
            jaConfirmouDecisao = false;
            jaMostrouDecisaoEvento = false;
            eventoAtual = null;
            window._eventoDepoisPendente = null;

            estadoAtual = dados;
            atualizarMesa(dados);
            mostrarRevelacao(dados);
            atualizarInfoRodada(dados);

            document.getElementById('painel-decisao').classList.add('escondido');

            const painelAntesRev2 = document.getElementById('painel-evento-antes');
            if (painelAntesRev2) painelAntesRev2.remove();

            const painelDiscRev2 = document.getElementById('painel-discussao');
            if (painelDiscRev2) painelDiscRev2.remove();

            const painelExtraRev2 = document.getElementById('painel-evento-extra');
            if (painelExtraRev2) {
                painelExtraRev2.innerHTML = '';
                painelExtraRev2.classList.add('escondido');
            }
            return;
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

        default:
             if (dados.contasPessoais) {
                estadoAtual = dados;
                atualizarMesa(dados);
                atualizarInfoRodada(dados);
                // Se fase é DECISAO e ainda não confirmou, mostra painel
                if ((dados.fase === 'DECISAO' || dados.fase === 'EM_JOGO')
                    && !jaConfirmouDecisao) {
                    mostrarFaseDecisao(dados);
                }
            }
            break;
    }

    if (dados.mensagem && tipo !== 'REVELACAO') {
        mostrarMensagemFlutuante(dados.mensagem);
    }
    if (dados.meta) {
        document.getElementById('meta-valor').textContent = dados.meta + ' 🪙';
        document.getElementById('meta-placar-valor').textContent = dados.meta;
    }
    if (dados.rodadaAtual) {
        document.getElementById('info-rodada').textContent =
            'Rodada ' + dados.rodadaAtual + '/' + dados.totalRodadas;
    }
}

function atualizarInfoRodada(dados) {
    if (dados.rodadaAtual) {
        document.getElementById('info-rodada').textContent =
            'Rodada ' + dados.rodadaAtual + '/' + dados.totalRodadas;
    }
    if (dados.fase) {
        const fases = {
            'DECISAO': 'Decidindo', 'EM_JOGO': 'Decidindo',
            'REVELACAO': 'Revelação', 'VOTACAO': 'Votação',
            'EVENTO': 'Evento', 'DISCUSSAO': 'Discussão', 'FIM': 'Fim de Jogo'
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
                style="${eliminado ? 'background:var(--perigo);opacity:0.5' : ''}">
                ${nome.charAt(0).toUpperCase()}
            </div>
            <div class="jogador-nome">
                ${nome}${souEu ? ' (você)' : ''}${eliminado ? ' ☠️' : ''}
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
    if (card) card.classList.add('eliminado-permanente');
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
    // Se há evento pendente, não mostra decisão ainda
    if (eventoPendente) {
        console.log('Evento pendente — aguardando conclusão antes da decisão');
        return;
    }
    const eliminados = dados.jogadoresEliminados || [];
    if (eliminados.includes(usernameAtual)) {
        document.getElementById('painel-decisao').classList.add('escondido');
        mostrarMensagemFlutuante('☠️ Você foi eliminado. Modo espectador ativado.');
        document.getElementById('info-fase').textContent = '☠️ Espectador';
        return;
    }

    if (jaConfirmouDecisao) return;

    moedasTributo = 0;
    moedasBem = 0;
    totalMoedas = dados.moedasDaRodada || 5;
    atualizarContadores();
    document.getElementById('painel-decisao').classList.remove('escondido');
    document.getElementById('btn-confirmar').disabled = false;
    document.getElementById('btn-confirmar').textContent = 'Confirmar';
    document.getElementById('info-fase').textContent = 'Decidindo';
    document.getElementById('info-fase').className = 'badge-fase fase-decisao';

    if (dados.decisaoEventoDepois && dados.eventoAtualInfo && !jaMostrouDecisaoEvento) {
        jaMostrouDecisaoEvento = true;
        eventoAtual = dados.eventoAtualInfo;
        setTimeout(() => mostrarInterfaceEvento(dados.eventoAtualInfo), 500);
    }
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

    jaConfirmouDecisao = true;
    document.getElementById('btn-confirmar').disabled = true;
    document.getElementById('btn-confirmar').textContent = '✅ Aguardando...';
    document.getElementById('decisao-erro').textContent = '';

    // Reexibe evento após decisão se estiver pendente
    if (window._eventoDepoisPendente) {
        setTimeout(() => {
            const painel = document.getElementById('painel-evento-extra');
            if (painel && painel.innerHTML === '') {
                mostrarInterfaceEvento(window._eventoDepoisPendente);
            }
        }, 300);
    }
}

// =============================================
// FASE DISCUSSÃO
// =============================================
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
    const podeVotar = dados.podeVotar === true;

    const jogadoresOpcoes = Object.keys(dados.contasPessoais || {})
        .filter(n => n !== usernameAtual &&
            !(dados.jogadoresEliminados || []).includes(n))
        .map(n => `<option value="${n}" style="color:black">${n}</option>`)
        .join('');

    const rodadeMetade = Math.floor((dados.totalRodadas || 0) / 2) + 1;

    const votacaoHtml = podeVotar
        ? `<div style="margin-top:12px;">
            <p style="color:var(--texto-fraco);font-size:0.85rem;margin-bottom:8px;">
                Escolha um jogador para eliminar:
            </p>
            <select id="select-eliminar"
                style="width:100%;padding:10px;margin-bottom:8px;
                background:white;color:black;border-radius:8px;
                font-size:0.95rem;border:none;">
                <option value="" style="color:black">Escolha quem eliminar...</option>
                ${jogadoresOpcoes}
            </select>
            <div style="display:flex;gap:8px;">
                <button class="btn-principal" id="btn-votar-eliminar"
                    onclick="votarEliminar()"
                    style="flex:2;background:var(--perigo);">
                    🗳️ Votar para Eliminar
                </button>
                <button class="btn-principal" id="btn-pular-voto"
                    onclick="pularVoto()"
                    style="flex:1;background:rgba(255,255,255,0.15);color:var(--texto);">
                    ⏭️ Pular
                </button>
            </div>
           </div>`
        : `<p style="color:var(--texto-fraco);text-align:center;
              font-size:0.82rem;margin-top:12px;padding:10px;
              background:rgba(255,255,255,0.05);border-radius:8px;">
              🔒 Votação disponível a partir da rodada ${rodadeMetade}
           </p>`;

    const painel = document.createElement('div');
    painel.id = 'painel-discussao';
    painel.className = 'painel-decisao';
    painel.innerHTML = `
        <div class="decisao-card">
            <h3>💬 Tempo de Discussão</h3>
            <div class="contagem-regressiva" id="contagem-discussao">${tempoTotal}</div>
            <p style="color:var(--texto-fraco);text-align:center;margin:8px 0;font-size:0.9rem;">
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
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
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

function mostrarInterfaceDistribuicaoLider(dados) {
    const painelExistente = document.getElementById('painel-evento-antes');
    if (painelExistente) painelExistente.remove();

    const jogadores = Object.keys(dados.contasPessoais || {});
    const totalMoedasLider = jogadores.length * 5; // cada jogador tinha 5

    const linhasJogadores = jogadores.map(nome => `
        <div style="display:flex; align-items:center; gap:8px; margin-bottom:8px;">
            <span style="flex:1; color:var(--texto);">${nome}</span>
            <div style="display:flex; align-items:center; gap:4px;">
                <label style="font-size:0.8rem; color:var(--texto-fraco);">Tributo:</label>
                <input type="number" id="tributo-${nome}" min="0" value="0"
                    style="width:50px; padding:4px; background:rgba(255,255,255,0.1);
                    border:1px solid rgba(255,255,255,0.2); border-radius:4px;
                    color:var(--texto); text-align:center;"
                    onchange="atualizarTotalLider()"/>
                <label style="font-size:0.8rem; color:var(--texto-fraco);">Bem:</label>
                <input type="number" id="bem-${nome}" min="0" value="0"
                    style="width:50px; padding:4px; background:rgba(255,255,255,0.1);
                    border:1px solid rgba(255,255,255,0.2); border-radius:4px;
                    color:var(--texto); text-align:center;"
                    onchange="atualizarTotalLider()"/>
            </div>
        </div>
    `).join('');

    const painel = document.createElement('div');
    painel.id = 'painel-evento-antes';
    painel.className = 'painel-decisao';
    painel.innerHTML = `
        <div class="decisao-card">
            <h3>👑 Você é o Líder! Distribua as moedas</h3>
            <p style="color:var(--texto-fraco); font-size:0.85rem; margin-bottom:12px;">
                Total disponível: <strong id="total-lider">${totalMoedasLider}</strong> moedas
                | Distribuídas: <strong id="distribuidas-lider">0</strong>
            </p>
            ${linhasJogadores}
            <button class="btn-principal" onclick="confirmarDistribuicaoLider()"
                style="margin-top:12px;">
                👑 Confirmar Distribuição
            </button>
            <p id="erro-lider" class="mensagem-erro"></p>
        </div>
    `;
    document.body.appendChild(painel);

    window._jogadoresLider = jogadores;
    window._totalMoedasLider = totalMoedasLider;
}

function atualizarTotalLider() {
    const jogadores = window._jogadoresLider || [];
    let total = 0;
    jogadores.forEach(nome => {
        total += parseInt(document.getElementById('tributo-' + nome)?.value || 0);
        total += parseInt(document.getElementById('bem-' + nome)?.value || 0);
    });
    const el = document.getElementById('distribuidas-lider');
    if (el) {
        el.textContent = total;
        el.style.color = total === window._totalMoedasLider
            ? 'var(--sucesso)' : 'var(--perigo)';
    }
}

async function confirmarDistribuicaoLider() {
    const jogadores = window._jogadoresLider || [];
    const total = window._totalMoedasLider || 0;

    let distribuicao = {};
    let soma = 0;

    jogadores.forEach(nome => {
        const tributo = parseInt(document.getElementById('tributo-' + nome)?.value || 0);
        const bem = parseInt(document.getElementById('bem-' + nome)?.value || 0);
        distribuicao[nome] = { tributo, bemPessoal: bem };
        soma += tributo + bem;
    });

    if (soma !== total) {
        document.getElementById('erro-lider').textContent =
            `Distribua exatamente ${total} moedas! (${soma} distribuídas)`;
        return;
    }

    await fetch('/jogo/evento/lideranca/distribuir', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ codigoSala, distribuicao })
    });

    const painel = document.getElementById('painel-evento-antes');
    if (painel) painel.innerHTML = `
        <div class="decisao-card">
            <p class="evento-info-neutro">👑 Distribuição confirmada!</p>
        </div>
    `;
}

function mostrarResultadoEvento(dados) {
    const overlayAnterior = document.getElementById('overlay-resultado-evento');
    if (overlayAnterior) overlayAnterior.remove();

    const isBolha = dados.nomeEvento === 'BOLHA';
    const corFundo = isBolha
        ? 'rgba(46,204,113,0.15)'
        : 'rgba(231,76,60,0.15)';
    const corBorda = isBolha ? 'var(--sucesso)' : 'var(--perigo)';

    const overlay = document.createElement('div');
    overlay.id = 'overlay-resultado-evento';
    overlay.style.cssText = `
        position: fixed; inset: 0;
        background: rgba(0,0,0,0.8);
        display: flex; align-items: center;
        justify-content: center; z-index: 300;
        animation: surgir 0.3s ease;
    `;
    overlay.innerHTML = `
        <div style="background: ${corFundo}; border-radius: 20px;
            padding: 48px; max-width: 500px; width: 90%;
            text-align: center; border: 2px solid ${corBorda};
            box-shadow: 0 0 40px ${corBorda};">
            <div style="font-size: 5rem; margin-bottom: 16px;
                animation: surgir 0.5s ease;">
                ${dados.emoji || '⚡'}
            </div>
            <h2 style="color: ${corBorda}; margin-bottom: 16px; font-size: 1.5rem;">
                ${isBolha ? '🫧 A Bolha Estourou!' : '🔥 A Fogueira Apagou!'}
            </h2>
            <div style="background: rgba(255,255,255,0.05); border-radius: 12px;
                padding: 20px; margin: 16px 0;">
                <p style="font-size: 1.1rem; color: var(--texto); line-height: 1.8;">
                    ${dados.mensagem}
                </p>
            </div>
            <div style="margin-top: 16px; color: var(--texto-fraco);
                font-size: 0.85rem;">
                Fechando em <span id="countdown-evento">5</span> segundos...
            </div>
        </div>
    `;
    document.body.appendChild(overlay);

    // Countdown
    let countdown = 5;
    const timer = setInterval(() => {
        countdown--;
        const el = document.getElementById('countdown-evento');
        if (el) el.textContent = countdown;
        if (countdown <= 0) {
            clearInterval(timer);
            overlay.remove();
        }
    }, 1000);
}

function mostrarCronometro(segundos) {
    const anterior = document.getElementById('cronometro-evento-fixo');
    if (anterior) anterior.remove();

    const el = document.createElement('div');
    el.id = 'cronometro-evento-fixo';
    el.style.cssText = `
        position: fixed; bottom: 24px; right: 24px;
        background: var(--destaque);
        border-radius: 16px; padding: 16px 28px;
        text-align: center; z-index: 9999;
        box-shadow: 0 4px 20px rgba(0,0,0,0.5);
        min-width: 120px;
    `;
    el.innerHTML = `
        <div style="font-size:0.8rem;color:#0f1923;margin-bottom:4px;
            font-weight:bold;">⏱️ Tempo restante</div>
        <div id="cronometro-valor" style="font-size:3.5rem;
            font-weight:bold;color:#0f1923;">${segundos}</div>
    `;
    document.body.appendChild(el);
}

function esconderCronometro() {
    const el = document.getElementById('cronometro-evento-fixo');
    if (el) el.remove();
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

    // Remove painéis
    ['painel-evento-antes','painel-discussao'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.remove();
    });

    const tributos = dados.totalTributosRodada || 0;
    const porJogador = dados.valorDistribuido || 0;
    const descartado = dados.moedasDescartadas || 0;
    const nJogadores = Object.keys(dados.contasPessoais || {}).length;

    // Monta lista de jogadores
    const jogadoresList = Object.entries(dados.contasPessoais || {})
        .sort((a, b) => b[1] - a[1])
        .map(([nome, conta]) => {
            const souEu = nome === usernameAtual;
            const eliminado = (dados.jogadoresEliminados || []).includes(nome);
            return `<div style="display:flex;justify-content:space-between;
                padding:5px 10px;border-radius:6px;margin-bottom:3px;
                background:${souEu?'rgba(240,192,64,0.1)':'rgba(255,255,255,0.03)'};
                ${eliminado?'opacity:0.4;':''}">
                <span style="color:${souEu?'var(--destaque)':'var(--texto)'}">
                    ${nome}${souEu?' (você)':''}${eliminado?' ☠️':''}
                </span>
                <span style="color:var(--destaque);font-weight:bold;">${conta} 🪙</span>
            </div>`;
        }).join('');

    // Cria overlay igual ao de evento
    const overlayAnterior = document.getElementById('overlay-revelacao');
    if (overlayAnterior) overlayAnterior.remove();

    const overlay = document.createElement('div');
    overlay.id = 'overlay-revelacao';
    overlay.className = 'overlay-evento';
    overlay.innerHTML = `
        <div class="evento-card" style="max-width:480px;">
            <div style="font-size:3rem;margin-bottom:8px;">🏛️</div>
            <h2 style="color:var(--destaque);margin-bottom:16px;">
                Resultado da Rodada
            </h2>
            <div style="display:flex;gap:8px;margin-bottom:16px;">
                <div style="flex:1;background:rgba(255,255,255,0.05);
                    border-radius:8px;padding:10px;text-align:center;">
                    <div style="font-size:0.75rem;color:var(--texto-fraco);
                        margin-bottom:4px;">💰 Arrecadado</div>
                    <div style="font-size:1.3rem;font-weight:bold;
                        color:var(--destaque);">${tributos} 🪙</div>
                </div>
                <div style="flex:1;background:rgba(46,204,113,0.08);
                    border-radius:8px;padding:10px;text-align:center;
                    border:1px solid rgba(46,204,113,0.2);">
                    <div style="font-size:0.75rem;color:var(--texto-fraco);
                        margin-bottom:4px;">📤 Por jogador</div>
                    <div style="font-size:1.3rem;font-weight:bold;
                        color:var(--sucesso);">+${porJogador} 🪙</div>
                </div>
                <div style="flex:1;background:rgba(231,76,60,0.05);
                    border-radius:8px;padding:10px;text-align:center;
                    border:1px solid rgba(231,76,60,0.15);">
                    <div style="font-size:0.75rem;color:var(--texto-fraco);
                        margin-bottom:4px;">🗑️ Descartado</div>
                    <div style="font-size:1.3rem;font-weight:bold;
                        color:var(--perigo);">${Math.max(0,descartado)} 🪙</div>
                </div>
            </div>
            <div style="margin-bottom:16px;">${jogadoresList}</div>
            <div style="background:rgba(255,255,255,0.1);
                border-radius:4px;height:4px;overflow:hidden;">
                <div id="barra-revelacao" style="height:100%;
                    background:var(--destaque);width:100%;
                    transition:width 0.1s linear;"></div>
            </div>
            <p style="text-align:center;color:var(--texto-fraco);
                font-size:0.75rem;margin-top:6px;">
                Fechando em <span id="countdown-revelacao">8</span>s
            </p>
        </div>
    `;
    document.body.appendChild(overlay);

    // Countdown 8 segundos
    let seg = 8;
    const barra = document.getElementById('barra-revelacao');
    const cd = document.getElementById('countdown-revelacao');
    const timer = setInterval(() => {
        seg -= 0.1;
        if (barra) barra.style.width = Math.max(0,(seg/8)*100) + '%';
        if (cd) cd.textContent = Math.ceil(seg);
        if (seg <= 0) {
            clearInterval(timer);
            overlay.style.opacity = '0';
            overlay.style.transition = 'opacity 0.3s';
            setTimeout(() => overlay.remove(), 300);
        }
    }, 100);
}

function mostrarPopupRevelacao(dados) {
    // Remove pop-up anterior se existir
    const anterior = document.getElementById('popup-revelacao');
    if (anterior) anterior.remove();

    const tributos = dados.totalTributosRodada || 0;
    const porJogador = dados.valorDistribuido || 0;
    const descartado = tributos > 0
        ? (tributos * 2) - (porJogador * Object.keys(dados.contasPessoais || {}).length)
        : 0;

    // Monta lista de jogadores com suas contas
    const jogadores = Object.entries(dados.contasPessoais || {})
        .sort((a, b) => b[1] - a[1])
        .map(([nome, conta]) => {
            const souEu = nome === usernameAtual;
            const eliminado = (dados.jogadoresEliminados || []).includes(nome);
            return `
                <div style="display:flex; justify-content:space-between;
                    align-items:center; padding:6px 12px;
                    background:${souEu ? 'rgba(240,192,64,0.1)' : 'rgba(255,255,255,0.03)'};
                    border-radius:6px; margin-bottom:4px;
                    ${eliminado ? 'opacity:0.4;' : ''}">
                    <span style="color:${souEu ? 'var(--destaque)' : 'var(--texto)'};">
                        ${nome}${souEu ? ' (você)' : ''}${eliminado ? ' ☠️' : ''}
                    </span>
                    <span style="color:var(--destaque); font-weight:bold;">
                        ${conta} 🪙
                    </span>
                </div>
            `;
        }).join('');

    const popup = document.createElement('div');
    popup.id = 'popup-revelacao';
    popup.style.cssText = `
        position: fixed; inset: 0;
        background: rgba(0,0,0,0.8);
        display: flex; align-items: center;
        justify-content: center; z-index: 250;
        animation: surgir 0.3s ease;
    `;
    popup.innerHTML = `
        <div style="background: var(--painel); border-radius: 20px;
            padding: 32px; max-width: 500px; width: 90%;
            border: 2px solid rgba(240,192,64,0.3);
            box-shadow: 0 0 40px rgba(240,192,64,0.15);">

            <h2 style="text-align:center; color:var(--destaque);
                margin-bottom:20px; font-size:1.3rem;">
                🏛️ Resultado da Rodada
            </h2>

            <!-- Linha 1: Total arrecadado -->
            <div style="display:flex; justify-content:space-between;
                background:rgba(255,255,255,0.05); border-radius:10px;
                padding:12px 16px; margin-bottom:8px;">
                <span style="color:var(--texto-fraco);">💰 Total nos Tributos</span>
                <span style="color:var(--destaque); font-weight:bold;
                    font-size:1.1rem;">${tributos} 🪙</span>
            </div>

            <!-- Linha 2: Recebido por jogador -->
            <div style="display:flex; justify-content:space-between;
                background:rgba(46,204,113,0.08); border-radius:10px;
                padding:12px 16px; margin-bottom:8px;
                border:1px solid rgba(46,204,113,0.2);">
                <span style="color:var(--texto-fraco);">
                    📤 Cada jogador recebeu
                </span>
                <span style="color:var(--sucesso); font-weight:bold;
                    font-size:1.1rem;">+${porJogador} 🪙</span>
            </div>

            <!-- Linha 3: Descartado -->
            <div style="display:flex; justify-content:space-between;
                background:rgba(231,76,60,0.05); border-radius:10px;
                padding:12px 16px; margin-bottom:20px;
                border:1px solid rgba(231,76,60,0.15);">
                <span style="color:var(--texto-fraco);">🗑️ Moedas descartadas</span>
                <span style="color:var(--perigo); font-weight:bold;">
                    ${descartado} 🪙
                </span>
            </div>

            <!-- Placar atual -->
            <h3 style="color:var(--texto-fraco); font-size:0.85rem;
                margin-bottom:10px; text-transform:uppercase;
                letter-spacing:1px;">📊 Placar Atual</h3>
            <div>${jogadores}</div>

            <!-- Barra de progresso do tempo -->
            <div style="margin-top:20px;">
                <div style="background:rgba(255,255,255,0.1);
                    border-radius:4px; height:4px; overflow:hidden;">
                    <div id="barra-revelacao" style="height:100%;
                        background:var(--destaque); width:100%;
                        transition:width 0.1s linear;"></div>
                </div>
                <p style="text-align:center; color:var(--texto-fraco);
                    font-size:0.75rem; margin-top:6px;">
                    Fechando em <span id="countdown-revelacao">8</span>s
                </p>
            </div>
        </div>
    `;
    document.body.appendChild(popup);

    // Contagem regressiva de 8 segundos
    let segundos = 8;
    const barra = document.getElementById('barra-revelacao');
    const countdown = document.getElementById('countdown-revelacao');

    const timer = setInterval(() => {
        segundos -= 0.1;
        const porcentagem = (segundos / 8) * 100;
        if (barra) barra.style.width = porcentagem + '%';
        if (countdown) countdown.textContent = Math.ceil(segundos);

        if (segundos <= 0) {
            clearInterval(timer);
            popup.style.opacity = '0';
            popup.style.transition = 'opacity 0.3s';
            setTimeout(() => popup.remove(), 300);
        }
    }, 100);
}

// =============================================
// EVENTOS
// =============================================
let eventoPendente = false;
let resolverEventoPendente = null;

function sinalizarEventoConcluido() {
    if (intervaloEventoAntes) {
        clearInterval(intervaloEventoAntes);
        intervaloEventoAntes = null;
    }
    esconderCronometro();
    window._alvoEventoSelecionado = null;
    eventoPendente = false;

    // Remove painel do evento
    setTimeout(() => {
        const p = document.getElementById('painel-evento-antes');
        if (p) p.remove();
    }, 800);

    // Mostra decisão das moedas após pequena pausa
    setTimeout(() => {
        if (estadoAtual && !jaConfirmouDecisao) {
            estadoAtual.fase = 'DECISAO';
            mostrarFaseDecisao(estadoAtual);
        }
    }, 1000);

    if (resolverEventoPendente) {
        resolverEventoPendente();
        resolverEventoPendente = null;
    }
}


function obterPainelEvento() {
    return document.getElementById('conteudo-evento-antes') ||
           document.getElementById('painel-evento-extra');
}

function mostrarEvento(evento) {
    if (!evento) return;

    const emojis = {
        ROUBO:'💰',VENENO:'🧪',PARCEIROS:'🤝',ROLETA:'🎰',
        DUPLICATA:'✌️',EXPOSICAO:'🔍',LIDERANCA:'👑',OSMOSE:'🧂',
        TRAICAO:'🔪',COPIA:'📄',BOLHA:'🫧',BOMBA_RELOGIO:'💣',
        IGUALDADE:'🟰',FOGUEIRA:'🔥'
    };
    const nomes = {
        ROUBO:'Roubo',VENENO:'Veneno',PARCEIROS:'Parceiros',
        ROLETA:'Roleta',DUPLICATA:'Duplicata',EXPOSICAO:'Exposição',
        LIDERANCA:'Liderança',OSMOSE:'Osmose',TRAICAO:'Traição',
        COPIA:'Cópia',BOLHA:'Bolha',BOMBA_RELOGIO:'Bomba-Relógio',
        IGUALDADE:'Igualdade',FOGUEIRA:'Fogueira'
    };

    document.getElementById('evento-emoji').textContent = emojis[evento.tipo]||'⚡';
    document.getElementById('evento-titulo').textContent = nomes[evento.tipo]||evento.tipo;
    document.getElementById('evento-descricao').textContent = evento.descricao||'';

    dispararAnimacaoEvento(evento.tipo);
    document.getElementById('overlay-evento').classList.remove('escondido');

    // Barra de progresso
    const barra = document.getElementById('barra-evento');
    let progresso = 100;
    const timerBarra = setInterval(() => {
        progresso -= (100/80); // 8 segundos = 80 intervalos de 100ms
        if (barra) barra.style.width = Math.max(0, progresso) + '%';
        if (progresso <= 0) clearInterval(timerBarra);
    }, 100);

    // Fecha após 8 segundos automaticamente
    setTimeout(() => {
        clearInterval(timerBarra);
        document.getElementById('overlay-evento').classList.add('escondido');
        document.getElementById('evento-animacao').innerHTML = '';
        eventoAtual = evento;
        // Mostra interface se necessário
        if (evento.requerDecisao) mostrarInterfaceEvento(evento);
    }, 8000);
}

function fecharEvento() {
    document.getElementById('overlay-evento').classList.add('escondido');
    document.getElementById('evento-animacao').innerHTML = '';
}

function obterEmojiEvento(tipo) {
    const emojis = {
        ROUBO: '💰', VENENO: '🧪', PARCEIROS: '🤝', ROLETA: '🎰',
        DUPLICATA: '✌️', EXPOSICAO: '🔍', LIDERANCA: '👑', OSMOSE: '🧂',
        TRAICAO: '🔪', COPIA: '📄', BOLHA: '🫧', BOMBA_RELOGIO: '💣',
        IGUALDADE: '🟰', FOGUEIRA: '🔥'
    };
    return emojis[tipo] || '⚡';
}

function dispararAnimacaoEvento(tipo) {
    let container = document.getElementById('evento-animacao');
    if (!container) return;
    container.innerHTML = '';

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
        if (animacoes[tipo]) animacoes[tipo]();
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
    // Coroa no canto superior direito
    const el = document.createElement('div');
    el.style.cssText = `
        position: absolute;
        top: 8px;
        right: 12px;
        font-size: 2.5rem;
        animation: coroaEntrada 0.6s ease forwards;
    `;
    el.textContent = '👑';
    c.appendChild(el);

    // Brilho sutil ao redor
    const brilho = document.createElement('div');
    brilho.style.cssText = `
        position: absolute;
        top: 0; right: 0;
        width: 80px; height: 80px;
        background: radial-gradient(circle,
            rgba(240,192,64,0.3) 0%, transparent 70%);
        animation: brilhar 1.5s ease-out forwards;
    `;
    c.appendChild(brilho);
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

// =============================================
// INTERFACES DOS EVENTOS
// =============================================
function mostrarInterfaceEvento(evento, containerOverride) {
    eventoAtual = evento;
    const painel = containerOverride || document.getElementById('painel-evento-extra');
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

let intervaloEventoAntes = null;

function mostrarInterfaceEventoAntes(evento) {
    const painelExistente = document.getElementById('painel-evento-antes');
    if (painelExistente) painelExistente.remove();

    if (intervaloEventoAntes) {
        clearInterval(intervaloEventoAntes);
        intervaloEventoAntes = null;
    }

    window._alvoEventoSelecionado = null;

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

    // Mostra cronômetro
    mostrarCronometro(45);
    let segundos = 45;

    intervaloEventoAntes = setInterval(() => {
    segundos--;
    const val = document.getElementById('cronometro-valor');
    if (val) {
        val.textContent = segundos;
        // Sempre preto, só muda o fundo do container
        val.style.color = '#0f1923';
        const container = document.getElementById('cronometro-evento-fixo');
        if (container) {
            if (segundos <= 10) container.style.background = 'var(--perigo)';
            else if (segundos <= 20) container.style.background = 'var(--destaque)';
        }
    }
    if (segundos <= 0) {
        clearInterval(intervaloEventoAntes);
        intervaloEventoAntes = null;
        esconderCronometro();
        executarAcaoPadrao(evento);
    }
}, 1000);
}

async function executarAcaoPadrao(evento) {
    // Ação padrão quando o tempo esgota
    const mensagens = {
        ROUBO: '💰 O ladrão não escolheu ninguém.',
        VENENO: '🧪 O Feiticeiro não escolheu nenhuma vítima.',
        PARCEIROS: '🤝 Tempo esgotado! Nenhuma ação dos parceiros.',
        ROLETA: '🎰 Tempo esgotado! Sem giros.',
        EXPOSICAO: '🔍 O Expositor não espiou ninguém.',
        OSMOSE: '🧂 Nenhum duelo foi iniciado.',
        TRAICAO: '🔪 O Traidor não escolheu ninguém.',
        BOMBA_RELOGIO: '💣 A bomba explodiu!',
        IGUALDADE: '🟰 Votação encerrada por tempo.',
        DUPLICATA: '✌️ Tempo esgotado! Nenhuma duplicata.'
    };

    const msg = mensagens[evento.tipo] || 'Tempo esgotado!';

    // Envia ação de recusar/pular para o servidor
    const acoesRecusar = {
        ROUBO: 'RECUSAR', VENENO: 'PULAR', PARCEIROS: 'PULAR',
        ROLETA: 'PARAR', EXPOSICAO: 'RECUSAR', OSMOSE: 'RECUSAR',
        TRAICAO: 'PULAR', BOMBA_RELOGIO: 'PULAR',
        IGUALDADE: 'NAO', DUPLICATA: 'NENHUM'
    };

    const acao = acoesRecusar[evento.tipo];
    if (acao) {
        await enviarAcaoEvento({ tipo: evento.tipo, acao });
    }

    // Atualiza o painel
    const conteudo = document.getElementById('conteudo-evento-antes');
    if (conteudo) {
        conteudo.innerHTML = `
            <div style="text-align:center; padding:16px;">
                <div style="font-size:2rem; margin-bottom:8px;">⏰</div>
                <p style="color:var(--texto-fraco);">${msg}</p>
            </div>
        `;
    }

    mostrarMensagemFlutuante('⏰ ' + msg);
    sinalizarEventoConcluido();
}

function seletorJogadores(excluirEuMesmo = true) {
    const jogadores = Object.keys(estadoAtual.contasPessoais || {})
        .filter(n => !(estadoAtual.jogadoresEliminados || []).includes(n))
        .filter(n => excluirEuMesmo ? n !== usernameAtual : true);

    const opcoes = jogadores
        .map(n => `<option value="${n}">${n}</option>`)
        .join('');

    // Pré-seleciona o primeiro se só houver um
    if (jogadores.length === 1) {
        window._alvoEventoSelecionado = jogadores[0];
    }

    return `<select class="select-evento" id="select-alvo-evento"
        style="color:black; background:white;"
        onchange="window._alvoEventoSelecionado = this.value">
        <option value="" style="color:black">Escolha um jogador...</option>
        ${opcoes}
    </select>`;
}

function obterAlvoSelecionado() {
    if (window._alvoEventoSelecionado) return window._alvoEventoSelecionado;
    const el = document.getElementById('select-alvo-evento') ||
               document.querySelector('.select-evento');
    return el ? el.value : null;
}

function mostrarInterfaceRoubo(evento, painel) {
    if (!evento.souExecutor) {
        painel.innerHTML = `<p class="evento-info-neutro">👀 Aguardando o ladrão escolher sua vítima...</p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>💰 Você é o <strong>Ladrão</strong>! Escolha quem roubar (ou não roube ninguém):</p>
            ${seletorJogadores()}
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="confirmarRoubo()">💰 Roubar</button>
                <button class="btn-evento-neutro" onclick="recusarRoubo()">Não roubar ninguém</button>
            </div>
        </div>
    `;
}

async function confirmarRoubo() {
    const alvo = obterAlvoSelecionado();
    window._alvoEventoSelecionado = null; // limpa após usar
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'ROUBO', acao: 'ROUBAR', alvo });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = '<p class="evento-info-neutro">✅ Ação registrada!</p>';
    sinalizarEventoConcluido();
}

async function recusarRoubo() {
    await enviarAcaoEvento({ tipo: 'ROUBO', acao: 'RECUSAR' });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = '<p class="evento-info-neutro">✅ Você optou por não roubar.</p>';
    sinalizarEventoConcluido();
}

function mostrarInterfaceVeneno(evento, painel) {
    if (!evento.souFeiticeiro) {
        painel.innerHTML = `<p class="evento-info-neutro">🧪 Aguardando o Feiticeiro escolher sua vítima...</p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🧪 Você é o <strong>Feiticeiro</strong>! Escolha quem envenenar:</p>
            <p class="evento-aviso">Se a vítima não colocar 5 moedas nos tributos, perderá 5 moedas!</p>
            ${seletorJogadores()}
            <button class="btn-evento-acao" onclick="confirmarVeneno()">🧪 Envenenar</button>
        </div>
    `;
}

async function confirmarVeneno() {
    const alvo = obterAlvoSelecionado();
    window._alvoEventoSelecionado = null; // limpa após usar
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'VENENO', acao: 'ENVENENAR', alvo });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = '<p class="evento-info-neutro">🧪 Veneno preparado!</p>';
    sinalizarEventoConcluido();
}

function mostrarInterfaceParceiros(evento, painel) {
    if (!evento.souParceiro) {
        painel.innerHTML = `<p class="evento-info-neutro">🤝 Aguardando os parceiros decidirem...</p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🤝 Você é <strong>Parceiro</strong>! Escolha sua ação:</p>
            <p class="evento-aviso">
                Ambos compartilham → bem-pessoal ×1.5<br>
                Você engana, outro compartilha → seu bem-pessoal ×2<br>
                Ambos enganam → nada acontece
            </p>
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="escolherParceiro('COMPARTILHAR')">🤝 Compartilhar</button>
                <button class="btn-evento-perigo" onclick="escolherParceiro('ENGANAR')">😈 Enganar</button>
            </div>
        </div>
    `;
}

async function escolherParceiro(acao) {
    await enviarAcaoEvento({ tipo: 'PARCEIROS', acao });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = `<p class="evento-info-neutro">✅ Você escolheu: ${acao}</p>`;
    sinalizarEventoConcluido();
}

function mostrarInterfaceRoleta(evento, painel) {
    window.girosRoleta = 0;
    window.maxGiros = 3;

    painel.innerHTML = `
        <div class="evento-interface">
            <p>🎰 <strong>Roleta!</strong> Você pode girar até 3 vezes:</p>
            <p class="evento-aviso">
                Cair em 1 ou 8 → bem-pessoal ×1.5<br>
                Qualquer outro → -5 moedas no bem-pessoal
            </p>
            <div id="roleta-resultado" style="font-size:2.5rem;text-align:center;
                margin:16px 0;min-height:50px;"></div>
            <div class="evento-botoes">
                <button class="btn-evento-acao" id="btn-girar"
                    onclick="girarRoleta()">
                    🎰 Girar (3 restantes)
                </button>
                <button class="btn-evento-neutro" id="btn-parar-roleta"
                    onclick="pararRoleta()">
                    Não girar mais
                </button>
            </div>
        </div>
    `;
}

async function girarRoleta() {
    if (window.girosRoleta >= window.maxGiros) return;
    window.girosRoleta++;
    const restantes = window.maxGiros - window.girosRoleta;

    const resultado = Math.floor(Math.random() * 8) + 1;
    const ganhou = resultado === 1 || resultado === 8;

    // Mostra resultado visual
    document.getElementById('roleta-resultado').innerHTML =
        `<span style="color:${ganhou ? 'var(--sucesso)' : 'var(--perigo)'}">
            ${resultado} ${ganhou ? '✅ ×1.5!' : '❌ -5'}
        </span>`;

    // Envia para o backend aplicar o efeito
    await enviarAcaoEvento({ tipo: 'ROLETA', acao: 'GIRAR', resultado });

    const btn = document.getElementById('btn-girar');
    if (btn) btn.textContent = `🎰 Girar (${restantes} restantes)`;

    if (restantes === 0) {
        if (btn) btn.disabled = true;
        const btnParar = document.getElementById('btn-parar-roleta');
        if (btnParar) btnParar.disabled = true;

        // Fecha automaticamente após 1.5s
        setTimeout(() => {
            const painel = obterPainelEvento();
            if (painel) painel.innerHTML =
                '<p class="evento-info-neutro">🎰 Roleta encerrada!</p>';
            sinalizarEventoConcluido();
        }, 1500);
    }
}

async function pararRoleta() {
    await enviarAcaoEvento({ tipo: 'ROLETA', acao: 'PARAR' });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML =
        '<p class="evento-info-neutro">🎰 Roleta encerrada!</p>';
    sinalizarEventoConcluido();
}

function mostrarInterfaceDuplicata(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>✌️ <strong>Duplicata!</strong> Após confirmar suas moedas, escolha uma opção:</p>
            <p class="evento-aviso">50% de chance de ×2 ou ÷2</p>
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="escolherDuplicata('TRIBUTO')">🏛️ Duplicar Tributos</button>
                <button class="btn-evento-acao" onclick="escolherDuplicata('BEM_PESSOAL')">💼 Duplicar Bem-pessoal</button>
                <button class="btn-evento-neutro" onclick="escolherDuplicata('NENHUM')">Nenhuma</button>
            </div>
        </div>
    `;
}

async function escolherDuplicata(opcao) {
    const resp = await enviarAcaoEvento({ tipo: 'DUPLICATA', acao: opcao });

    const painel = document.getElementById('painel-evento-extra') ||
                   document.getElementById('conteudo-evento-antes');

    if (painel) {
        const msg = resp?.mensagem || '✅ Duplicata registrada!';
        const cor = resp?.dobrou === true ? 'var(--sucesso)' :
                    resp?.dobrou === false ? 'var(--perigo)' : 'var(--texto-fraco)';

        painel.innerHTML = `
            <div style="text-align:center; padding:16px;">
                <div style="font-size:2rem; margin-bottom:12px;">
                    ${resp?.dobrou === true ? '✅' : resp?.dobrou === false ? '❌' : '⚪'}
                </div>
                <p style="color:${cor}; font-size:1rem; font-weight:bold;">
                    ${msg}
                </p>
            </div>
        `;
    }
    sinalizarEventoConcluido();
}

function mostrarInterfaceExposicao(evento, painel) {
    if (!evento.souExpositor) {
        painel.innerHTML = `<p class="evento-info-neutro">🔍 O Expositor está espiando alguém...</p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🔍 Você é o <strong>Expositor</strong>! Escolha quem espiar (opcional):</p>
            ${seletorJogadores(false)}
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="confirmarExposicao()">🔍 Espiar</button>
                <button class="btn-evento-neutro" onclick="recusarExposicao()">Não espiar ninguém</button>
            </div>
            <div id="resultado-exposicao" style="margin-top:12px;"></div>
        </div>
    `;
}

async function confirmarExposicao() {
    const alvo = obterAlvoSelecionado();
    window._alvoEventoSelecionado = null; // limpa após usar
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    const resp = await enviarAcaoEvento({ tipo: 'EXPOSICAO', acao: 'ESPIAR', alvo });
    const painel = obterPainelEvento();
    if (painel && resp?.bemPessoal !== undefined) {
        painel.innerHTML = `
            <p style="color:var(--destaque); text-align:center;">
                💼 ${alvo} tem <strong>${resp.bemPessoal} moedas</strong> no bem-pessoal!
            </p>`;
    }
    sinalizarEventoConcluido();
}

async function recusarExposicao() {
    await enviarAcaoEvento({ tipo: 'EXPOSICAO', acao: 'RECUSAR' });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = '<p class="evento-info-neutro">✅ Você optou por não espiar.</p>';
    sinalizarEventoConcluido();
}
function mostrarInterfaceLideranca(evento, painel) {
    if (!evento.souExecutor) {
        painel.innerHTML = `<p class="evento-info-neutro">
            👑 Aguardando o Líder ser revelado...
        </p>`;
    }
}

async function votarLideranca() {
    const alvo = document.getElementById('select-alvo-evento')?.value;
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }

    await fetch('/jogo/evento/lideranca/votar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ codigoSala, alvo })
    });

    document.getElementById('painel-evento-extra') &&
        (document.getElementById('painel-evento-extra').innerHTML =
            '<p class="evento-info-neutro">✅ Voto registrado!</p>');

    const conteudoAntes = document.getElementById('conteudo-evento-antes');
    if (conteudoAntes) conteudoAntes.innerHTML =
        '<p class="evento-info-neutro">✅ Voto registrado! Aguardando...</p>';
        const painel = document.getElementById('painel-evento-antes');
    if (painel) painel.innerHTML =
        '<div class="decisao-card"><p class="evento-info-neutro">👑 Voto Computado!</p></div>';
    sinalizarEventoConcluido();
}

function atualizarPlacarLideranca(votos) {
    const placar = document.getElementById('placar-lideranca');
    if (!placar) return;
    placar.innerHTML = Object.entries(votos)
        .map(([nome, qtd]) => `${nome}: ${qtd} voto(s)`)
        .join(' | ');
}

async function votarLider() {
    const alvo = obterAlvoSelecionado();
    window._alvoEventoSelecionado = null; // limpa após usar
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'LIDERANCA', acao: 'VOTAR', alvo });
    document.getElementById('painel-evento-extra').innerHTML =
        `<p class="evento-info-neutro">✅ Você votou em ${alvo}!</p>`;
}

function mostrarInterfaceOsmose(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🧂 <strong>Osmose!</strong> Desafie alguém para um duelo (após confirmar moedas):</p>
            <p class="evento-aviso">Quem tiver mais bem-pessoal rouba 3 moedas do outro.</p>
            ${seletorJogadores()}
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="confirmarOsmose()">⚔️ Desafiar</button>
                <button class="btn-evento-neutro" onclick="recusarOsmose()">Não desafiar</button>
            </div>
        </div>
    `;
}

async function confirmarOsmose() {
    const alvo = obterAlvoSelecionado();
    window._alvoEventoSelecionado = null; // limpa após usar
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    const resp = await enviarAcaoEvento({ tipo: 'OSMOSE', acao: 'DESAFIAR', alvo });
    let msg = '⚔️ Duelo realizado!';
    if (resp?.resultado === 1) msg = '⚔️ Você venceu! +3 moedas!';
    else if (resp?.resultado === -1) msg = '⚔️ Você perdeu! -3 moedas.';
    else if (resp?.resultado === 0) msg = '⚔️ Empate!';
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = `<p class="evento-info-neutro">${msg}</p>`;
    sinalizarEventoConcluido();
}

async function recusarOsmose() {
    await enviarAcaoEvento({ tipo: 'OSMOSE', acao: 'RECUSAR' });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = '<p class="evento-info-neutro">✅ Você optou por não desafiar.</p>';
    sinalizarEventoConcluido();
}

function mostrarInterfaceTraicao(evento, painel) {
    if (evento.souTraidor) {
        painel.innerHTML = `
            <div class="evento-interface">
                <p>🔪 Você é o <strong>Traidor</strong>! Escolha quem trair:</p>
                ${seletorJogadores()}
                <button class="btn-evento-perigo" onclick="confirmarTraicao()">🔪 Trair</button>
            </div>
        `;
    } else if (evento.souVitima) {
        painel.innerHTML = `
            <div class="evento-interface">
                <p>🔪 Você foi <strong>traído</strong>! Tente adivinhar quem foi o traidor:</p>
                ${seletorJogadores()}
                <button class="btn-evento-acao" onclick="adivinharTraidor()">🔍 Acusar</button>
            </div>
        `;
    } else {
        painel.innerHTML = `<p class="evento-info-neutro">🔪 Um traidor está agindo nas sombras...</p>`;
    }
}

async function confirmarTraicao() {
    const alvo = obterAlvoSelecionado();
    window._alvoEventoSelecionado = null; // limpa após usar
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'TRAICAO', acao: 'TRAIR', alvo });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = '<p class="evento-info-neutro">🔪 Traição registrada!</p>';
    sinalizarEventoConcluido();
}

async function adivinharTraidor() {
    const alvo = obterAlvoSelecionado();
    window._alvoEventoSelecionado = null; // limpa após usar
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    const resp = await enviarAcaoEvento({ tipo: 'TRAICAO', acao: 'ADIVINHAR', alvo });
    const msg = resp?.acertou
        ? '🎯 Você acertou! O traidor perdeu 3 moedas!'
        : '❌ Você errou! Perdeu 3 moedas.';
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = `<p class="evento-info-neutro">${msg}</p>`;
    sinalizarEventoConcluido();
}

function mostrarInterfaceCopia(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>📄 <strong>Cópia!</strong> Após confirmar moedas, você pode copiar o bem-pessoal de alguém:</p>
            ${seletorJogadores()}
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="confirmarCopia()">📄 Copiar</button>
                <button class="btn-evento-neutro" onclick="recusarCopia()">Não copiar ninguém</button>
            </div>
        </div>
    `;
}

async function confirmarCopia() {
    const alvo = obterAlvoSelecionado();
    window._alvoEventoSelecionado = null; // limpa após usar
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    await enviarAcaoEvento({ tipo: 'COPIA', acao: 'COPIAR', alvo });

    const painelExtra = document.getElementById('painel-evento-extra');
    const painelAntes = document.getElementById('conteudo-evento-antes');
    const msg = `<p class="evento-info-neutro">📄 Bem-pessoal de ${alvo} copiado!</p>`;
    if (painelAntes) painelAntes.innerHTML = msg;
    if (painelExtra) painelExtra.innerHTML = msg;
    sinalizarEventoConcluido();
}

async function recusarCopia() {
    await enviarAcaoEvento({ tipo: 'COPIA', acao: 'RECUSAR' });
    const painelExtra = document.getElementById('painel-evento-extra');
    const painelAntes = document.getElementById('conteudo-evento-antes');
    const msg = '<p class="evento-info-neutro">✅ Você optou por não copiar.</p>';
    if (painelAntes) painelAntes.innerHTML = msg;
    if (painelExtra) painelExtra.innerHTML = msg;
    sinalizarEventoConcluido();
}

function mostrarInterfaceIgualdade(evento, painel) {
    painel.innerHTML = `
        <div class="evento-interface">
            <p>🟰 <strong>Igualdade!</strong> Votem: devemos igualar todos os bem-pessoais?</p>
            <div class="evento-botoes">
                <button class="btn-evento-acao" onclick="votarIgualdade(true)">✅ Sim, igualar</button>
                <button class="btn-evento-perigo" onclick="votarIgualdade(false)">❌ Não igualar</button>
            </div>
        </div>
    `;
}

async function votarIgualdade(sim) {
    await enviarAcaoEvento({ tipo: 'IGUALDADE', acao: sim ? 'SIM' : 'NAO' });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = '<p class="evento-info-neutro">✅ Voto registrado!</p>';
    sinalizarEventoConcluido();
}

function mostrarInterfaceBomba(evento, painel) {
    if (!evento.souPortador) {
        painel.innerHTML = `<p class="evento-info-neutro">💣 A bomba está passando de mão em mão...</p>`;
        return;
    }
    painel.innerHTML = `
        <div class="evento-interface">
            <p>💣 Você está com a <strong>Bomba-Relógio</strong>! Passe para alguém antes que exploda!</p>
            ${seletorJogadores()}
            <button class="btn-evento-perigo" onclick="passarBomba()">💣 Passar a Bomba</button>
        </div>
    `;
}

async function passarBomba() {
    const alvo = obterAlvoSelecionado();
    window._alvoEventoSelecionado = null; // limpa após usar
    if (!alvo) { mostrarMensagemFlutuante('Selecione um jogador!'); return; }
    const resp = await enviarAcaoEvento({ tipo: 'BOMBA_RELOGIO', acao: 'PASSAR', alvo });
    const painel = obterPainelEvento();
    if (painel) painel.innerHTML = resp?.explodiu
        ? '<p class="evento-info-neutro">💥 A bomba explodiu!</p>'
        : `<p class="evento-info-neutro">💣 Bomba passada para ${alvo}!</p>`;
    sinalizarEventoConcluido();
}

async function enviarAcaoEvento(dados) {
    try {
        const resp = await fetch('/jogo/evento/acao', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ codigoSala, ...dados })
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
        body: JSON.stringify({ idVotacao: idVotacaoAtiva, favor, codigoSala })
    });
    document.querySelector('.votacao-botoes').innerHTML =
        '<p class="mensagem-sucesso">Voto registrado!</p>';
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