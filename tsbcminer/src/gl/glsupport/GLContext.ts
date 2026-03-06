
export function createGLContext(): WebGL2RenderingContext {
    const canvas = document.createElement('canvas');
    const context = canvas.getContext('webgl2');
    if (!context) throw new Error('No webgl2 context');
    return context;
}

export function glEnumName(constant: GLenum): string {
    const names = WebGL2RenderingContext as unknown as Record<string, number>;
    return Object.keys(names).find(k => names[k] === constant) || constant.toString();
}