import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Scanner;

/**
 * Configurador de Domínio Privado para Termux
 * Cria um domínio local (.local) para acessar seu site Java no Termux
 */
public class TermuxDomainSetup {
    
    private static final String HOSTS_FILE = "/data/data/com.termux/files/usr/etc/hosts";
    private static final String LOCALHOST = "127.0.0.1";
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║  Configurador de Domínio Privado      ║");
        System.out.println("║  Termux - Site Java                   ║");
        System.out.println("╚════════════════════════════════════════╝\n");
        
        try {
            System.out.print("📝 Digite o nome do seu domínio (sem .local): ");
            String domainName = scanner.nextLine().trim();
            
            if (domainName.isEmpty()) {
                System.out.println("❌ Nome de domínio não pode estar vazio!");
                return;
            }
            
            System.out.print("🔌 Digite a porta do seu app Java (padrão 8080): ");
            String portInput = scanner.nextLine().trim();
            int port = portInput.isEmpty() ? 8080 : Integer.parseInt(portInput);
            
            // Adicionar domínio ao arquivo hosts
            addDomainToHosts(domainName);
            
            // Criar servidor proxy
            System.out.println("\n🚀 Iniciando servidor proxy...\n");
            startProxyServer(domainName, port);
            
        } catch (NumberFormatException e) {
            System.out.println("❌ Porta deve ser um número válido!");
        } catch (Exception e) {
            System.out.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
    
    /**
     * Adiciona o domínio ao arquivo /etc/hosts do Termux
     */
    private static void addDomainToHosts(String domain) throws Exception {
        String localDomain = domain + ".local";
        File hostsFile = new File(HOSTS_FILE);
        
        if (!hostsFile.exists()) {
            hostsFile.getParentFile().mkdirs();
            hostsFile.createNewFile();
        }
        
        // Ler conteúdo atual
        String content = new String(Files.readAllBytes(hostsFile.toPath()));
        
        // Verificar se já existe
        if (content.contains(localDomain)) {
            System.out.println("✅ Domínio " + localDomain + " já configurado!");
            return;
        }
        
        // Adicionar nova entrada
        String newEntry = LOCALHOST + "   " + localDomain + "   www." + localDomain + "\n";
        Files.write(hostsFile.toPath(), (content + newEntry).getBytes());
        
        System.out.println("✅ Domínio " + localDomain + " adicionado com sucesso!");
    }
    
    /**
     * Inicia um servidor proxy que redireciona para seu app Java
     */
    private static void startProxyServer(String domain, int appPort) throws Exception {
        String localDomain = domain + ".local";
        int proxyPort = 80;
        
        System.out.println("📍 Proxy escutando em: http://" + localDomain);
        System.out.println("🎯 Redirecionando para: http://localhost:" + appPort);
        System.out.println("\n💡 Certifique-se de que seu app Java está rodando na porta " + appPort);
        System.out.println("⏸️  Pressione Ctrl+C para parar o servidor\n");
        
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(proxyPort), 0);
            
            server.createContext("/", exchange -> {
                try {
                    // Conectar ao app Java
                    URL url = new URL("http://localhost:" + appPort + exchange.getRequestURI());
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod(exchange.getRequestMethod());
                    
                    // Copiar headers
                    exchange.getRequestHeaders().forEach((key, values) -> {
                        if (!key.equals("Host")) {
                            conn.setRequestProperty(key, values.get(0));
                        }
                    });
                    
                    // Obter resposta
                    int responseCode = conn.getResponseCode();
                    exchange.sendResponseHeaders(responseCode, -1);
                    
                    // Copiar body
                    try (InputStream in = conn.getInputStream();
                         OutputStream out = exchange.getResponseBody()) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                    
                } catch (Exception e) {
                    System.err.println("⚠️  Erro ao conectar: " + e.getMessage());
                    try {
                        String error = "❌ Erro ao conectar ao app Java na porta " + appPort;
                        exchange.sendResponseHeaders(502, error.length());
                        exchange.getResponseBody().write(error.getBytes());
                        exchange.close();
                    } catch (IOException ignored) {}
                }
            });
            
            server.start();
            System.out.println("✅ Servidor proxy iniciado com sucesso!");
            
        } catch (BindException e) {
            System.out.println("⚠️  A porta 80 já está em uso!");
            System.out.println("💡 Tente usar uma porta diferente (ex: 8000)");
            System.out.println("   Edite este código e mude: int proxyPort = 80; para int proxyPort = 8000;");
        }
    }
}

/**
 * Classe auxiliar para gerenciar o servidor HTTP
 */
class HttpServer {
    public static com.sun.net.httpserver.HttpServer create(
            InetSocketAddress address, int backlog) throws IOException {
        return com.sun.net.httpserver.HttpServer.create(address, backlog);
    }
}
