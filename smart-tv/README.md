# 📺 Guia de Instalação e Execução em Smart TVs (LG, Samsung, Android TV) • by FBG2

Este guia explica como transformar e rodar o projeto **MK21 Streaming** diretamente em Smart TVs **LG (webOS)**, **Samsung (Tizen OS)**, **Android TV**, **Fire TV Stick** e **TV Box**.

---

## 🚀 Método 1: Acesso Direto pelo Navegador da TV (Sem Instalar Nada)
Todas as Smart TVs modernas (Samsung 2017+, LG 2016+, Philips, TCL, Roku) possuem um navegador web integrado de alta velocidade.

1. Abra o **Navegador de Internet** da sua Smart TV (Samsung Internet ou Navegador Web da LG).
2. Digite o endereço do seu app:
   ```text
   https://fbgs-streaming.vercel.app/web/index.html
   ```
3. Faça o login com suas credenciais (ficarão salvas na memória da TV).
4. Pressione o botão de **Tela Cheia** ou adicione a página aos **Favoritos da TV** para abrir sempre com 1 clique!
5. O controle remoto (D-Pad, setas, Voltar e Ok) funciona de forma 100% nativa.

---

## 🔴 Método 2: LG Smart TV (webOS) — Como Pacote Nativo (.IPK)
Os aplicativos da LG Smart TV usam a tecnologia oficial **webOS TV SDK** (baseada em HTML5/JS).

### Arquivos prontos no repositório:
- `smart-tv/mk21play_3.6.0_all.ipk`: Pacote oficial compilado pronto para instalação.
- `smart-tv/mk21-tv/`: Código fonte completo otimizado para webOS e Tizen.
- `smart-tv/webos/appinfo.json` e `smart-tv/mk21-tv/appinfo.json`: Manifestos webOS v3.6.0.

### Instalação via Homebrew Channel (Recomendado):
1. Caso sua TV possua o **webOS Homebrew Channel**, transfira e instale diretamente o arquivo `mk21play_3.6.0_all.ipk` ou use o instalador OTA integrado no menu Configurações > Atualização de Sistema.

### Instalação via Modo Desenvolvedor oficial:
1. **Ative o Modo Desenvolvedor na sua TV LG:**
   - Na loja de aplicativos da LG TV (**LG Content Store**), pesquise e instale o aplicativo **Developer Mode**.
   - Abra o app, crie uma conta gratuita LG Developer e ative o **Dev Mode Status**. A TV mostrará seu IP e a chave de acesso.
2. **No seu computador (Windows/Mac/Linux):**
   - Baixe o pacote oficial [webOS TV CLI (ares-cli)](https://webostv.developer.lge.com/develop/tools/cli-installation).
   - Instale na sua TV com o comando:
     ```bash
     ares-install smart-tv/mk21play_3.6.0_all.ipk -d <NomeDaSuaTV>
     ```
3. O ícone **MK21 Play** aparecerá na barra principal de aplicativos da sua LG TV!

---

## 🔵 Método 3: Samsung Smart TV (Tizen OS) — Como Pacote Nativo (.WGT)
Os aplicativos da Samsung TV rodam sob o sistema operacional **Tizen OS**.

### Arquivos prontos no repositório:
- `smart-tv/tizen/config.xml`: Manifesto com suporte a resolução 4K/FullHD e permissões de controle remoto.

### Passos para instalar na Samsung TV:
1. **Ative o Modo Desenvolvedor na Samsung TV:**
   - No menu da TV, vá em **Apps**.
   - No controle remoto, aperte a sequência numérica: `1 2 3 4 5`.
   - Um pop-up "Developer Mode" aparecerá. Marque **ON** e digite o IP local do seu computador.
   - Reinicie a TV (segurando o botão Power por 3 segundos).
2. **No seu computador:**
   - Instale o [Tizen Studio com TV Extensions](https://developer.tizen.org/development/tizen-studio/download).
   - No terminal, compile o widget `.wgt`:
     ```bash
     tizen package -t wgt -s <NomeDoSeuCertificado> -- smart-tv/tizen
     ```
   - Envie para a TV conectada:
     ```bash
     tizen install -n Mk21Stream.wgt -t <ID_DA_TV>
     ```
3. O aplicativo aparecerá na gaveta de aplicativos da sua TV Samsung.

---

## 🟢 Método 4: Android TV / Google TV / Fire TV Stick / TV Box
Para dispositivos Android (Xiaomi Mi Box, Chromecast com Google TV, Firestick, Realme TV, etc.):

1. No Firestick ou TV Box, instale o aplicativo gratuito **Downloader**.
2. Digite a URL direta do APK gerado pelo Android Studio / Gradle ou acesse a URL da Vercel.
3. Se preferir rodar no navegador da TV Box, instale o **JioPages** ou **TV Bro** e abra a URL da Vercel.

---

## 🎮 Mapeamento de Teclas do Controle Remoto nas TVs:

| Tecla do Controle | Função no MK21 Web |
| :--- | :--- |
| **Seta Cima / Canal +** | Canal anterior |
| **Seta Baixo / Canal -** | Próximo canal |
| **Seta Esquerda** | Diminui volume ou volta 10s no filme |
| **Seta Direita** | Aumenta volume ou avança 10s no filme |
| **OK / Enter** | Seleciona canal / Abre menu |
| **Voltar / Return (10009 / 461)** | Fecha telas cheias, modais ou fecha o player |
| **Play / Pause (415 / 19)** | Pausa ou retoma o vídeo |
| **Botão Vermelho (403)** | Alterna entre **Ao Vivo**, **Filmes** e **Séries** |
| **Botão Verde (404)** | **Atualizar Lista** do servidor |
| **Botão Amarelo (405)** | Abre o menu de **Espelhar na TV** |
| **Botão Azul (406)** | Alterna **Tela Cheia** |

---
*Desenvolvido com excelência • by FBG2*
