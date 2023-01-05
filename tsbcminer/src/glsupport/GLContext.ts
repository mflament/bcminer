
export function createGLContext(): WebGL2RenderingContext {
    const canvas = document.createElement('canvas');
    const context = canvas.getContext('webgl2');
    if (!context) throw new Error('No webgl2 context');
    return context;
}
