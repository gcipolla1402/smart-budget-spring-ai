package dio.budgeting.infrastructure.ai;

public class AiServiceUnavailableException extends RuntimeException {
    public AiServiceUnavailableException() {
        super("Serviço de Inteligência Artificial não configurado neste ambiente");
    }
}
