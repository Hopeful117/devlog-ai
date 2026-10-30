PRODUCT_OPENAI_CHATGPT = "openai_chatgpt"
ENGINE_CHATGPT = "chatgpt"


def engine_provider(provider: str) -> str:
    return ENGINE_CHATGPT if provider in {PRODUCT_OPENAI_CHATGPT, ENGINE_CHATGPT} else provider
